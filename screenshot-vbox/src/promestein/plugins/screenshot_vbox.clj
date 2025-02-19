(ns promestein.plugins.screenshot-vbox
  (:require [mount.core :as mount :refer [defstate]])
  (:require [clojure.java.jdbc :as db])
  (:require [clojure.tools.logging :as log])

  (:require [promestein.vbox.screenshot :refer :all])
  (:require [promestein.pages :as pages])
  (:require [promestein.platform :as p])
  (:require [promestein.server :refer :all]))

(defn- ensure-vbox-home
  []
  (when (nil? (System/getProperty "vbox.home"))
    (->> "VirtualBox" p/find-program .getParent (System/setProperty "vbox.home"))))

(defn- post-image
  "Post an image to Promestein as a page."
  [png-data]
  (let [name "screenshot.png"]
    (db/with-db-transaction [user-conn user-db]
      (let [opts {:processor pages-processor
                  :user-conn user-conn}
            contents (pages/make-image-page-contents png-data opts)]
        (pages/post-page {:plugin :image
                          :name name
                          :contents contents}
                         opts)))))

(defn post-screenshot
  "Posts a screenshot of current active VM."
  []
  (ensure-vbox-home)
  (let [manager (create-manager)]
    (try
      (if-let [image (get-active-machine-screenshot manager)]
        (post-image image)
        (log/info "Could not get a screenshot from VirtualBox"))
      (finally (destroy-manager manager)))))
