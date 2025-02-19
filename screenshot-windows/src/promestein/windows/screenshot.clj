(ns promestein.windows.screenshot
  (:import (java.awt Robot Rectangle)
           (jnr.ffi LibraryLoader Pointer Struct)
           (promestein.windows Win32Rectangle)))

(definterface User32
  (^jnr.ffi.Pointer GetForegroundWindow)
  (^int GetWindowRect [^jnr.ffi.Pointer windowHandle ^jnr.ffi.Pointer rectangle]))

(def user32-lib (delay (.. (LibraryLoader/create User32) (load "User32"))))
(def user32-runtime (delay (jnr.ffi.Runtime/getRuntime @user32-lib)))

(defn unpack-win32-rectangle-coordinates
  "Returns coordinates of a Win32Rectangle structure."
  [wrect]
  [(.get (.left wrect))
   (.get (.top wrect))
   (.get (.right wrect))
   (.get (.bottom wrect))])

(defn get-active-window-coordinates
  "Returns active window coordinates and size."
  []
  (let [lib @user32-lib
        runtime @user32-runtime
        active-window-rectangle (Win32Rectangle. runtime)
        active-window-handle (.GetForegroundWindow lib)
        active-window-rectangle-pointer (Struct/getMemory active-window-rectangle)]
    (.GetWindowRect lib active-window-handle active-window-rectangle-pointer)
    (.useMemory active-window-rectangle active-window-rectangle-pointer)
    (let [[left top right bottom] (unpack-win32-rectangle-coordinates active-window-rectangle)]
      [left top (- right left) (- bottom top)])))

(defn take-screenshot
  "Takes screenshot of the active window."
  []
  (let [bot (Robot.)
        [x y width height] (get-active-window-coordinates)
        rectangle (Rectangle. x y width height)]
    (.createScreenCapture bot rectangle)))
