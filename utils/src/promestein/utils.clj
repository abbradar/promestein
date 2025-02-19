(ns promestein.utils
  "Promestein-specific utils"
  (:import [java.io PushbackReader])
  (:require [clojure.java.io :as io])
  (:require [hikari-cp.core :as hikari])
  (:require [clojure.edn :as edn])
  (:require [cheshire.core :as json]))

(set! *warn-on-reflection* true)

;; open_mode support for SQLite
(defmethod hikari/translate-property :open-mode [_] "open_mode")

(defn start-sqlite-database
  "Start a Hikari pool for given SQLite database."
  ([db-path]
   (start-sqlite-database db-path {}))
  ([db-path {:keys [trace read-only] :or {trace false read-only false}}]
   (let [conn-options (if trace
                        {:jdbc-url (str "jdbc:p6spy:sqlite:" db-path)
                         :driver-class-name "com.p6spy.engine.spy.P6SpyDriver"}
                        {:jdbc-url (str "jdbc:sqlite:" db-path)})
         options (if read-only
                   (assoc conn-options
                          :read-only true
                          :open-mode 1)
                   conn-options)
         datasource (hikari/make-datasource options)
         spec {:datasource datasource}]
     spec)))

(defn close-database
  "Stop a Hikari pool."
  [db-spec]
  (-> db-spec :datasource hikari/close-datasource))

(defn to-json-keyword
  "Replace dashes with underscores."
  [^String keyword]
  (.replace keyword \- \_))

(defn from-json-keyword
  "Replace underscores with dashes."
  [^String keyword]
  (.replace keyword \_ \-))

(def sqlite-options
  "SQLite JDBC options."
  {:entities to-json-keyword
   :identifiers from-json-keyword})

(defn read-json-file
  "Read JSON file."
  [file-path]
  (with-open [file (io/reader file-path)]
    (json/parse-stream file true)))

(defn read-edn-file
  "Read EDN file."
  [file-path]
  (with-open [file (io/reader file-path)]
    (let [reader (PushbackReader. file)]
      (edn/read reader))))

;;; CORS wrapper for Ring

(defn cors-headers [_]
  {"Access-Control-Allow-Origin"  "*"
   "Access-Control-Allow-Headers" "Authorization, Content-Type"
   "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE"})

(defn cors-response [response params]
  (update response :headers merge (cors-headers params)))

(defn preflight? [request]
  (= (request :request-method) :options))

(defn preflight-response [params]
  {:status  200
   :headers (cors-headers params)
   :body    "preflight complete"})

(defn wrap-cors [handler & {:as params}]
  (fn
    ([request]
     (if (preflight? request)
       (preflight-response params)
       (some-> (handler request) (cors-response params))))

    ([request respond raise]
     (if (preflight? request)
       (respond (preflight-response params))
       (handler request
                #(some-> % (cors-response params) respond)
                #(some-> % (cors-response params) raise))))))
