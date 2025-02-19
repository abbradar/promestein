(ns promestein.pages
  (:import [java.io ByteArrayInputStream])
  (:require [clojure.java.jdbc :as db])
  (:require [clojure.core.async :as a])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [cheshire.core :as json])
  (:require [honeysql.core :as sql])
  (:require [honeysql.helpers :as h])
  (:require [org.tobereplaced.mapply :refer [mapply]])
  (:require [ring.middleware.params :refer [wrap-params]])
  (:require [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:require [ring.middleware.multipart-params.byte-array :refer [byte-array-store]])
  (:require [ring.middleware.json :refer [json-response wrap-json-response]])
  (:require [compojure.core :refer :all])
  (:require [ring.util.response :refer [response bad-request not-found]])
  (:require [clojure.tools.logging :as log])
  (:require [org.httpkit.server :as hk])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])
  (:require [promestein.jmdict :as jmdict])
  (:require [promestein.text :as text])
  (:require [promestein.images :as images]))

;;; Shared resources

(defn create-pages-processor
  "Create pages processor shared resources. Dictionary database is not considered owned."
  [& {:keys [google-credentials-path dict-db plugins] :as opts}]
  (let [text (text/create-text-processor :google-credentials-path google-credentials-path :dict-db dict-db)
        images (images/create-image-processor :google-credentials-path google-credentials-path :text-processor text)
        pages-chan (a/chan)]
    {:dict-db dict-db
     :text text
     :images images
     :plugins plugins
     :pages-chan pages-chan
     :pages-pub (a/pub pages-chan :topic)}))

(defn close-pages-processor
  "Close pages processor resources."
  [{:keys [text images]}]
  (images/close-image-processor images)
  (text/close-text-processor text))

;;; State database

(defn create-pages-db
  "Create pages state database."
  ([db-spec]
   (create-pages-db db-spec {}))
  ([db-spec create-options]
   (text/create-text-db db-spec create-options)
   (images/create-images-db db-spec create-options)
   (db/with-db-transaction [conn db-spec]
     (let [my-options (merge create-options sqlite-options)]
       (db/execute!
        conn (db/create-table-ddl
              :pages
              [[:id       "integer primary key autoincrement"]
               [:name     "text not null"]
               [:plugin   "text not null"]
               [:contents "text not null"]]
              my-options))))))

;;; Middleware

(defn page-to-info
  "Gets summary from a page."
  [{:keys [name plugin contents plugin-info] :as page} options]
  {:type plugin
   :name name
   :contents ((:info plugin-info) page options)})

(defn wrap-page [handler page]
  "Adds `page` entry to request."
  (fn [request]
    (if (some? page)
      (handler (assoc request :page page))
      (handler request))))

(defn- make-page-plugin-routes
  "Creates page API for a resolved plugin."
  [{:keys [plugin-info] :as page}]
  (apply routes
         (GET "/" [] (->
                      #(response (page-to-info page %))
                      wrap-json-response))
         (or (->> (:extra-routes plugin-info) (map #(-> % (wrap-page page)))) [])))

(defn get-page
  "Gets page by id."
  [page-id {:keys [processor user-conn] :as options}]
  (let [query (sql/format {:select [:name :plugin :contents]
                           :from [:pages]
                           :where [:= :id page-id]})]
    (when-let [{:keys [plugin contents] :as page} (first (db/query user-conn query sqlite-options))]
      (let [plugin-type (keyword plugin)]
        (when-let [plugin-info (plugin-type (:plugins processor))]
          (assoc page :plugin plugin-type :plugin-info plugin-info :contents (json/decode contents true)))))))

(defn- make-page-routes
  "Creates page API."
  [page-id]
  (fn [{:keys [user-conn] :as request}]
    (if-let [page (get-page page-id request)]
      ((make-page-plugin-routes page) request)
      (not-found "Not found"))))

(defn- wrap-pages
  "Adds common pages resources."
  [handler {:keys [processor user-db]}]
  (fn [request]
    (db/with-db-transaction [user-conn user-db]
      (handler (assoc request
                      :processor processor
                      :user-conn user-conn)))))

(defn post-page
  "Create new page with a specified plugin, name and contents. Return id."
  [{:keys [plugin contents] :as page} {:keys [processor user-conn] :as options}]
  (let [page-data {:plugin (name plugin)
                   :name (:name page)
                   :contents (json/encode contents)}
        id (-> (db/insert! user-conn :pages page-data sqlite-options)
               first
               vals
               first)]
    (a/>!! (:pages-chan processor) {:topic :event :type :new-page :id id :plugin plugin :name (:name page)})
    (log/infof "Created new page: id %d, plugin %s, name %s, contents %s" id plugin (:name page) contents)
    id))

(defn get-pages
  "Get list of all pages."
  [{:keys [processor user-conn] :as options}]
  (let [query
        (->>
         (-> (h/select :id :name :plugin :contents)
             (h/from :pages))
         sql/format)]
    (doall
     (->> (db/query user-conn query sqlite-options)
          (map #(merge
                 % (when-let [get-stats (-> processor :plugins (get (-> % :plugin keyword)) :stats)]
                     (->
                      (update % :contents json/decode true)
                      (get-stats options)))))
          (map #(dissoc % :contents))))))

(defn make-pages-routes
  "Creates pages API."
  [& {:keys [processor user-db] :as options}]
  (routes
   (GET "/events" []
     (fn [request] ; Ring asynchronous handlers aren't supported in http-kit, so we implement SSE by ourselves.
       (let [event-chan (a/chan)
             event-sub (a/sub (:pages-pub processor) :event event-chan)]
         (hk/with-channel request channel
           (hk/send! channel {:status 200
                              :headers
                              (merge (cors-headers nil)
                                     {"Content-Type"  "text/event-stream"
                                      "Cache-Control" "no-cache"
                                      "Connection"    "keep-alive"})}
                     false) ; don't close the socket
           (a/go-loop []
             (when-let [event (a/<! event-chan)]
               (let [encoded-event (-> event
                                       (dissoc :topic)
                                       json/encode)]
                 (hk/send! channel (str "data: " encoded-event "\n\n") false)
                 (recur))))
           (hk/on-close channel (fn [status] (a/close! event-chan)))))))
   (->
    (routes
     (POST "/new/:plugin-name" [plugin-name]
       (let [plugin-type (keyword plugin-name)]
         (->
          (fn [{:keys [user-conn] :as request}]
            (if-let [plugin (plugin-type (:plugins processor))]
              (let [pages ((:post plugin) request)
                    page-ids
                    (->> pages
                         (map #(-> %
                                   (assoc :plugin plugin-type)
                                   (post-page request))))]
                (json-response (response (->> page-ids (map #(hash-map :id %)))) {}))
              (bad-request "Unknown plugin")))
          wrap-params
          (wrap-multipart-params {:store (byte-array-store)}))))

     (GET "/" []
       (->
        (fn [{:keys [user-conn] :as request}]
          (response (get-pages request)))
        wrap-json-response))
     (context "/:page-id{[0-9]+}" [page-id] (make-page-routes (parse-int page-id))))
    (wrap-pages options))))

;;; Plugins

(defn make-text-page-contents
  "Make page contents with text."
  [text {:keys [processor user-conn] :as options}]
  (log/info "Preparing new text page")
  (let [[chunk-id] (text/process-chunks (:text processor) user-conn [text])]
    {:chunk-id chunk-id}))

(defn get-text
  "Get text from page."
  [page {:keys [user-conn] :as options}]
  (text/get-chunk user-conn (-> page :contents :chunk-id)))

(def text-plugin
  "Plugin for handling plain text."
  {:post (fn [{:keys [params] :as request}]
           ;; :processor in req contains pages processor
           ;; :user-conn contains connection to user database
           ;; :params contains multi-part params
           ;; Should return vector of default name and plugin-specific page contents which will be stored as JSON
           [{:name     (or (get params "name") "Unnamed")
             :contents (make-text-page-contents (get params "text") request)}])
   :info (fn [page {:keys [processor user-conn] :as options}]
           ;; :page contains page-specific data
           (get-text page options))
   :stats (fn [page {:keys [user-conn] :as options}]
            (text/get-chunk-stats user-conn (-> page :contents :chunk-id)))})

(defn make-image-page-contents
  "Make page contents with an image."
  [bytes {:keys [processor user-conn] :as options}]
  (log/info "Preparing new image page")
  (let [[image-id] (images/process-images (:images processor) user-conn [bytes])]
    {:image-id image-id}))

(defn get-image-info
  "Get image info from page."
  [page {:keys [user-conn] :as options}]
  (images/get-image-info user-conn (-> page :contents :image-id)))

(defn get-image-stats
  "Get image stats from page."
  [page {:keys [user-conn] :as options}]
  (images/get-image-stats user-conn (-> page :contents :image-id)))

(defn get-image-bytes
  "Get image mime and data from page."
  [page {:keys [user-conn] :as options}]
  (images/get-image-bytes user-conn (-> page :contents :image-id)))

(def image-plugin
  "Plugin for handling images"
  {:post (fn [{:keys [params] :as request}]
           (let [images
                 (as-> (get params "image") x
                   (cond-> x (not (vector? x)) vector))]
             (->> images
                  (map #(let [{:keys [filename bytes]} %]
                          {:name     filename
                           :contents (make-image-page-contents bytes request)})))))
   :info (fn [page options]
           (get-image-info page options))
   :stats (fn [page options]
            (get-image-stats page options))
   :extra-routes [(GET "/image" []
                    (fn [{:keys [page] :as request}]
                      (let [{:keys [mime data]} (get-image-bytes page request)]
                        {:status 200
                         :headers {"Content-Type" mime}
                         :body (ByteArrayInputStream. data)})))]})

