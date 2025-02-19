(ns promestein.server
  (:require [mount.core :as mount :refer [defstate]])
  (:require [org.httpkit.server :as hk])
  (:require [clojure.java.io :as io])
  (:require [clojure.string :as s])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [clojure.tools.logging :as log])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])
  (:require [promestein.api :refer :all])
  (:require [promestein.classpath :as cp])
  (:require [promestein.pages :as pages])
  (:require [promestein.jmdict :as jmdict]))

;;; Utils

(defn load-config
  "Load Promestein configuration."
  []
  (read-json-file (nth *command-line-args* 0 "config.json")))

(defn- load-plugin
  "Load plugin from a configuration file."
  [url]
  (let [config (read-edn-file url)]
    (when-let [ns (:ns config)]
      (log/infof "Loading plugin %s" ns)
      (-> ns symbol require))))

(defn load-plugins
  "Load plugins from a given path."
  [path]
  (let [file (io/file path)
        fs (file-seq file)
        jars (filter #(-> % .getName s/lower-case (s/ends-with? ".jar")) fs)]
    (run! cp/add-classpath jars))

  (let [cl (cp/classloader-tip)
        plugins (->> (.getResources cl "plugin.edn")
                     enumeration-seq
                     (map (fn [url] [(str url) url]))
                     (into {})
                     vals)]
    (run! load-plugin plugins)))

;;; Shared resources

;; This doesn't use mount because config is read to initialize plugins, which must happen before mount/start.
(def config (delay (load-config)))

(defstate dict-db
  :start (jmdict/open-dict-db (:dictionary @config))
  :stop (close-database dict-db))

(defstate pages-processor
  :start (pages/create-pages-processor
          :google-credentials-path (:google-credentials @config)
          :dict-db dict-db
          :plugins pages-plugins)
  :stop (pages/close-pages-processor pages-processor))

(defstate user-db
  :start (open-user-db (:state @config))
  :stop (close-database user-db))

(defstate server
  :start (let [app (make-app
                    :dict-db dict-db
                    :user-db user-db
                    :pages-processor pages-processor)]
           (hk/run-server app {:port (:port @config)}))
  :stop (server :timeout 100))
