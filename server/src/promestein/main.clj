(ns promestein.main
  (:require [mount.core :as mount :refer [defstate]])
  (:require [clojure.tools.logging :as log])

  (:require [promestein.server :refer :all])

  (:gen-class))

;;; Entry point

(defn -main
  [& args]
  (when-let [plugins (:plugins @config)]
    (load-plugins plugins))
  (mount/start)
  (log/info "Server is ready, backend on port" (:port @config)))
