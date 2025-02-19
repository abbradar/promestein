(ns promestein.x11.screenshot
  (:import [java.nio ByteBuffer ByteOrder])
  (:import [jnr.ffi LibraryLoader Pointer NativeLong Struct])
  (:import [jnr.ffi.byref IntByReference NativeLongByReference PointerByReference])
  (:import [jnr.ffi.provider ParameterFlags])
  (:import [java.awt.image BufferedImage DataBufferInt])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [clojure.tools.logging :as log])

  (:import [promestein.x11
            X11 X11$XImage X11$XErrorHandler X11$XErrorEvent X11$Status
            X11$XWindowAttributes X11$ImageFormat X11$ByteOrder])
  (:import [promestein.x11 XShm XShm$XShmSegmentInfo])
  (:import [promestein.posix SHM]))

(def x11-library
  (delay
    (let [lib (.. (LibraryLoader/create X11) (load "X11"))
          runtime (jnr.ffi.Runtime/getRuntime lib)
          handler (proxy [X11$XErrorHandler] []
                    (call [display event-ptr]
                      (let [event (doto (X11$XErrorEvent. runtime) (.useMemory event-ptr))]
                        (log/errorf
                         "X11 error, type %d, code %d, request %d:%d"
                         (.. event type get)
                         (.. event error_code get)
                         (.. event request_code get)
                         (.. event minor_code get))
                        0)))]
      (.XSetErrorHandler lib handler)
      lib)))

(def x11-runtime
  (delay (jnr.ffi.Runtime/getRuntime @x11-library)))

(def shm-library
  (delay (.. (LibraryLoader/create SHM) (load "c"))))

(def xshm-library
  (delay (.. (LibraryLoader/create XShm) (load "Xext"))))

(def xshm-runtime
  (delay (jnr.ffi.Runtime/getRuntime @xshm-library)))

(defn get-focus-window
  "Get X11 focused window."
  [display]
  (let [^X11 lib @x11-library
        focus-ref (NativeLongByReference.)
        revert-to-ref (IntByReference.)]
    (if (not= (.XGetInputFocus lib display focus-ref revert-to-ref) 1)
      nil
      (let [focus (.. focus-ref getValue)]
        (if (or
             (= focus 0)
             (= focus (X11/PointerRoot)))
          nil
          focus)))))

(defn get-root-window
  "Get X11 root window."
  [display]
  (let [^X11 lib @x11-library]
    (.XDefaultRootWindow lib display)))

(defn get-active-window
  "Get X11 active window via _NET_ACTIVE_WINDOW."
  [display]
  (let [^X11 lib @x11-library
        screen (.XDefaultScreen lib display)
        active-atom (.XInternAtom lib display "_NET_ACTIVE_WINDOW" false)]
    (if (= active-atom 0)
      nil
      (let [root (.XRootWindow lib display screen)]
        (when-not (= root 0)
          (let [actual-type-ref (NativeLongByReference.)
                actual-format-ref (IntByReference.)
                n-items-ref (NativeLongByReference.)
                bytes-after-ref (NativeLongByReference.)
                prop-ref (PointerByReference.)]
            (when (=
                   (.XGetWindowProperty
                    lib
                    display ; display
                    root ; w
                    active-atom ; property
                    (NativeLong. 0) ; long_offset
                    (NativeLong. (long (/ (Long/MAX_VALUE) 4))) ; long_length
                    false ; delete
                    (X11/XA_WINDOW) ; reg_type
                    actual-type-ref ; actual_type_return
                    actual-format-ref ; actual_format_return
                    n-items-ref ; nitems_return
                    bytes-after-ref ; bytes_after_return
                    prop-ref ; prop_return
                    )
                   (X11$Status/Success))
              (let [actual-type (.. actual-type-ref getValue)
                    actual-format (.. actual-format-ref getValue)
                    n-items (.. n-items-ref getValue longValue)
                    bytes-after (.. bytes-after-ref getValue longValue)
                    prop (.. prop-ref getValue)]
                (when-not (nil? prop)
                  (try
                    (when-not (= actual-type 0)
                      (when
                       (or (not= actual-type (X11/XA_WINDOW))
                           (not= actual-format 32)
                           (not= n-items 1)
                           (not= bytes-after 0))
                        (throw (ex-info "Unexpected _NET_ACTIVE_WINDOW return" {:actual-type actual-type
                                                                                :actual-format actual-format
                                                                                :n-items n-items
                                                                                :bytes-after bytes-after})))
                      (let [window-id (.getNativeLong prop 0)]
                        (when-not (= window-id 0) (NativeLong. window-id))))
                    (finally (.XFree lib prop))))))))))))

(defn- get-window-attributes
  "Get attributes of a given X11 window."
  [display window]
  (let [^X11 lib @x11-library
        ^jnr.ffi.Runtime runtime @x11-runtime
        attrs (X11$XWindowAttributes. runtime)
        attrs-storage (Struct/getMemory attrs)]
    (when (= (.XGetWindowAttributes lib display window attrs-storage) 1)
      (let [width (.. attrs width get)
            height (.. attrs height get)
            depth (.. attrs depth get)
            screen (.. attrs screen get)
            visual (.. attrs visual get)]
        {:width width
         :height height
         :depth depth
         :screen screen
         :visual visual}))))

(defn- read-ximage
  "Read raw XImage."
  [raw-image-ptr]
  (let [^X11 lib @x11-library
        ^jnr.ffi.Runtime runtime @x11-runtime
        raw-image (doto (X11$XImage. runtime) (.useMemory raw-image-ptr))
        width (.. raw-image width get)
        height (.. raw-image height get)
        bits-per-pixel (.. raw-image bits_per_pixel get)
        bitmap-pad (.. raw-image bitmap_pad get)
        byte-order (.. raw-image byte_order get)
        red-mask (.. raw-image red_mask get)
        green-mask (.. raw-image green_mask get)
        blue-mask (.. raw-image blue_mask get)
        image (BufferedImage. width height (BufferedImage/TYPE_INT_RGB))
        ^ints image-buffer (.getData (cast DataBufferInt (.. image getRaster getDataBuffer)))]
    (if (and (= blue-mask 0xff)
             (= green-mask 0xff00)
             (= red-mask 0xff0000)
             (= bits-per-pixel 32)
             (= bitmap-pad 32)
             (= byte-order (X11$ByteOrder/LSBFirst)))
      ;; fast path
      (.get (.. raw-image data get) 0 image-buffer 0 (* width height))
      ;; slow path
      (let [combined-mask (bit-or red-mask green-mask blue-mask)]
        (log/warnf "Slow path while reading XImage: %s" {:blue-mask blue-mask
                                                         :green-mask green-mask
                                                         :red-mask red-mask
                                                         :bits-per-pixel bits-per-pixel})
        (binding [*unchecked-math* :warn-on-boxed]
          (doseq [x (range 0 width)
                  y (range 0 height)]
            (let [pixel (.intValue (.XGetPixel lib raw-image-ptr x y))]
              (aset image-buffer (+ (* y width) x) (bit-and combined-mask pixel)))))))
    image))

(defn- get-window-screenshot-shm
  "Get a screenshot of a given X11 window via XShm extension."
  [display window]
  (let [^X11 lib @x11-library
        ^SHM shm-lib @shm-library
        ^XShm xshm-lib @xshm-library
        ^jnr.ffi.Runtime runtime @x11-runtime
        ^jnr.ffi.Runtime xshm-runtime @xshm-runtime]
    (when-let [{:keys [width height depth visual] :as attrs} (get-window-attributes display window)]
      (let [shm-segment (XShm$XShmSegmentInfo. xshm-runtime)
            ;; DIRECT means "allocate a block of memory and use it directly instead of copying to and from native
            shm-segment-storage (Struct/getMemory shm-segment (ParameterFlags/DIRECT))]
        (when-let [raw-image-ptr (.XShmCreateImage
                                  xshm-lib
                                  display ; dpy
                                  visual ; visual
                                  depth ; depth
                                  (X11$ImageFormat/ZPixmap) ; format
                                  nil ; data
                                  shm-segment-storage ; shminfo
                                  width ; width
                                  height ; height
                                  )]
          (try
            (let [raw-image (doto (X11$XImage. runtime) (.useMemory raw-image-ptr))
                  bytes-per-line (.. raw-image bytes_per_line get)
                  size (* bytes-per-line height)
                  shm-id (.shmget shm-lib (SHM/IPC_PRIVATE) size 0777)]
              (when-not (= shm-id -1)
                (try
                  (let [shm-ptr (.shmat shm-lib shm-id nil 0)]
                    (when-not (= (.address shm-ptr) -1)
                      (try
                        (doto raw-image
                          (.. data (set shm-ptr)))
                        (doto shm-segment
                          (.. shmid (set shm-id))
                          (.. shmaddr (set shm-ptr))
                          (.. readOnly (set 0)))
                        (when (.XShmAttach xshm-lib display shm-segment-storage)
                          (try
                            (when (.XShmGetImage
                                   xshm-lib
                                   display ; dpy
                                   window ; d
                                   raw-image-ptr ; image
                                   0 ; x
                                   0 ; y
                                   (X11/AllPlanes) ; plane_mask
                                   )
                              (read-ximage raw-image-ptr))
                            (finally (.XShmDetach xshm-lib display shm-segment-storage))))
                        (finally (.shmdt shm-lib shm-ptr)))))
                  (finally (.shmctl shm-lib shm-id (SHM/IPC_RMID) nil)))))
            (finally (.XDestroyImage lib raw-image-ptr))))))))

(defn- get-window-screenshot-x11
  "Get a screenshot of a given X11 window via Xlib."
  [display window]
  (let [^X11 lib @x11-library
        ^jnr.ffi.Runtime runtime @x11-runtime]
    (when-let [{:keys [width height]} (get-window-attributes display window)]
      (let [raw-image-ptr (.XGetImage
                           lib
                           display ; display
                           window ; d
                           0 ; x
                           0 ; y
                           width ; width
                           height ; height
                           (X11/AllPlanes) ; plane_mask
                           (X11$ImageFormat/ZPixmap) ; format
                           )]
        (when-not (nil? raw-image-ptr)
          (try
            (read-ximage raw-image-ptr)
            (finally (.XDestroyImage lib raw-image-ptr))))))))

(defn get-window-screenshot
  "Get a screenshot of a given X11 window."
  [display window]
  (let [^XShm xshm-lib @xshm-library]
    (if (.XShmQueryExtension xshm-lib display)
      (get-window-screenshot-shm display window)
      (get-window-screenshot-x11 display window))))

(defn open-display
  "Open X11 display."
  []
  (.XOpenDisplay @x11-library nil))

(defn close-display
  "Close X11 display."
  [display]
  (.XCloseDisplay @x11-library display))
