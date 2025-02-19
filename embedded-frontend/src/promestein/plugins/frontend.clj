(ns promestein.plugins.frontend
  (:require [clojure.tools.logging :as log])
  (:require [mount.core :as mount :refer [defstate]])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [cheshire.core :as json])
  (:require [clojure.java.io :as io])

  (:require [org.httpkit.server :as http-server])
  (:require [compojure.core :refer [routes GET]])
  (:require [compojure.response :refer [render]])
  (:require [compojure.route :as route :refer [not-found]])
  (:require [ring.util.mime-type :refer [default-mime-types]])
  (:require [ring.util.response :refer [url-response content-type]])

  (:require [promestein.server :as server]))

(defn otherwise [body]
  (fn [request]
    (-> (render body request))))

(defn routes-frontend []
  (routes
   (route/resources "/")
   (GET "/config.json" []
     (fn [req]
       (let [cfg (-> @server/config
                     :frontend
                     (dissoc :port)
                     (assoc :backend-url (str "http://<hostname>:" (:port @server/config))))]
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body (json/encode cfg)})))
   (GET "/*" []
     (fn [req] (->
                (url-response (io/resource "public/index.html"))
                (content-type (get default-mime-types "html")))))))

(defn app []
  (routes
   (routes-frontend)))

(defstate frontend
  :start
  (let [port (-> @server/config :frontend :port)]
    (log/info "Starting frontend on port" port)
    (http-server/run-server (app) {:port port}))

  :stop
  (frontend :timeout 100))


