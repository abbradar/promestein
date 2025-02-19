(ns promestein.api
  (:require [clojure.tools.trace :refer [trace]])
  (:require [org.tobereplaced.mapply :refer [mapply]])
  (:require [compojure.core :refer [routes context GET POST PUT]])
  (:require [ring.util.response :refer [response not-found]])
  (:require [ring.middleware.json :refer [json-response wrap-json-body]])
  (:require [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:require [clojure.stacktrace :refer [print-stack-trace]])
  (:require [clojure.string :as str])
  (:require [ring.util.response :refer [content-type]])
  (:require [ring.util.mime-type :as mime])
  (:require [clj-stacktrace.repl :refer [pst-on]])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])
  (:require [promestein.pages :as pages])
  (:require [promestein.text :as text])
  (:require [promestein.jmdict :as jmdict])
  (:require [promestein.flashcards :as flashcards]))

;;; User database

(defn open-user-db
  "Open user state database."
  ([db-path]
   (open-user-db db-path {}))
  ([db-path {:keys [trace] :or {trace false}}]
   (let [spec (start-sqlite-database db-path {:trace trace})]
     (pages/create-pages-db spec {:conditional? true})
     spec)))

;;; Misc

(defn exception-response [e]
  (pst-on *err* false e)
  {:status 500
   :body "Something went wrong"})

(defn wrap-exceptions [handler]
  "Prints stack trace and yields response with a generic error message."
  (fn
    ([request]
     (try
       (handler request)
       (catch Exception e
         (exception-response e))))

    ([request respond raise]
     (handler request respond #(-> % exception-response respond)))))

(defn wrap-otherwise [handler alt-handler]
  "Uses `alt-handler` if `handler` dindn't match the request (i.e. returned `nil`)."
  (fn
    ([request]
     (or (handler request) (alt-handler request)))

    ([request respond raise]
     (handler request
              #(if (some? %)
                 (respond %)
                 (alt-handler request respond raise))
              raise))))

;;; Routes

(def pages-plugins
  "Used plugins."
  {:text pages/text-plugin
   :image pages/image-plugin})

(defn- make-pages-routes
  [& {:keys [pages-processor user-db]}]
  (pages/make-pages-routes
   :processor pages-processor
   :user-db user-db))

(defn make-app
  "Create Promestein API."
  [& {:keys [pages-processor dict-db user-db] :as options}]
  (->
   (routes
    (context "/pages" [] (mapply make-pages-routes options))

    (GET "/dictionary/query" [& {:keys [normal-form] :as params}]
      (fn [request]
        (let [detector
              (-> pages-processor :text :detector)

              filter-entry-by
              (if (.hasKanji detector normal-form)
                jmdict/filter-entry-by-kanji
                jmdict/filter-entry-by-reading)

              pos
              (->>
               (some-> params :pos (str/split #","))
               (filter #(-> % empty? not))
               (into []))

              langs
              (->>
               (some-> params :lang (str/split #","))
               (filter #(-> % empty? not))
               (into #{}))

              entries
              (cond->>
               (jmdict/get-entries-by-reading dict-db normal-form)
                :always
                (map #(-> (jmdict/format-entry %)
                          (filter-entry-by normal-form)))

                (-> pos empty? not)
                (map #(update % :sense jmdict/filter-sense-by-pos
                              (partial jmdict/match-pos-unidic-jmdict pos)))

                (-> langs empty? not)
                (map #(update % :sense jmdict/filter-sense-by-lang langs))

                :always
                (filter #(-> % :sense empty? not)))]

          (json-response (response entries) {}))))

    (GET "/dictionary/entities" []
      (fn [request]
        (json-response
         (response
          (->> (jmdict/get-entities dict-db))) {})))

    (GET "/dictionary/:entry-id{[0-9]+}" [entry-id]
      (fn [request]
        (if-let [entry (jmdict/get-entry-by-id dict-db (parse-int entry-id))]
          (json-response (response (jmdict/format-entry entry)) {})
          (not-found "Not found"))))

    (GET "/chunks/:chunk-id{[0-9]+}" [chunk-id]
      (fn [request]
        (if-let [entry (text/get-chunk user-db (parse-int chunk-id))]
          (json-response (response entry) {})
          (not-found "Not found"))))

    (PUT "/tokens" []
      (->
       (fn [request]
         (doseq [token-id (-> request :body :ids)]
           (text/update-token user-db token-id (-> request :body :params)))
         (json-response (response "OK") {}))
       (wrap-json-body {:keywords? true})))

    (GET "/flashcards" []
      (fn [request]
        (->
         (response (flashcards/render-flashcards options))
         (content-type (-> mime/default-mime-types (get "txt")))))))

   (wrap-defaults api-defaults)
   (wrap-otherwise
    (fn [_]
      {:status 404
       :body "Not found"}))
   (wrap-exceptions)
   (wrap-cors)))
