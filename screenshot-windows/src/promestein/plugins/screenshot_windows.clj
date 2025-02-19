(ns promestein.plugins.screenshot-windows
  (:import (javax.imageio ImageIO)
           (promestein.windows Win32Rectangle))
  (:require [clojure.java.jdbc :as db])

  (:require [promestein.pages :as pages])
  (:require [promestein.server :refer :all])
  (:require [promestein.windows.screenshot :refer :all]))

(defn post-image
  "Post an image to Promestein as a page."
  [image]
  (let [stream (java.io.ByteArrayOutputStream.)
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
  "Posts screenshot to Promestein"
  []
  (when-let [shot (take-screenshot)]
    (post-image shot)))
