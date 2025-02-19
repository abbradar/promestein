(ns promestein.plugins.screenshot-x11
  (:import [java.io ByteArrayOutputStream])
  (:import [javax.imageio ImageIO])
  (:require [mount.core :as mount :refer [defstate]])
  (:require [clojure.java.jdbc :as db])

  (:require [promestein.x11.screenshot :refer :all])
  (:require [promestein.pages :as pages])
  (:require [promestein.server :refer :all]))

(defstate display
  :start (open-display)
  :stop (close-display display))

(defn- get-active-window-screenshot
  "Get active window screenshot."
  []
  (get-window-screenshot display (get-active-window display)))

(defn- get-screenshot
  "Get full screenshot."
  []
  (get-window-screenshot display (get-root-window display)))

(defn- post-image
  "Post an image to Promestein as a page."
  [image]
  (let [stream (ByteArrayOutputStream.)
        name "screenshot.png"]
    (ImageIO/write image "png" stream)
    (db/with-db-transaction [user-conn user-db]
      (let [opts {:processor pages-processor
                  :user-conn user-conn}
            contents (pages/make-image-page-contents (.toByteArray stream) opts)]
        (pages/post-page {:plugin :image
                          :name name
                          :contents contents}
                         opts)))))

(defn post-screenshot
  "Post a full screenshot to Promestein as a page."
  []
  (when-let [shot (get-screenshot)]
    (post-image shot)))

(defn post-active-window-screenshot
  "Post an active window screenshot to Promestein as a page."
  []
  (when-let [shot (get-active-window-screenshot)]
    (post-image shot)))
