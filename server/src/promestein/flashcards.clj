;; TODO: Once `make-app` gains extensibility, this should be moved to a separateplugin

(ns promestein.flashcards
  (:require [clojure.tools.trace :refer [trace]])
  (:require [cheshire.core :as json])
  (:require [clojure.java.io :as io])
  (:require [clojure.string :as str])

  (:require [org.httpkit.server :as http-server])
  (:require [compojure.core :refer [routes GET]])
  (:require [compojure.response :refer [render]])
  (:require [compojure.route :as route :refer [not-found]])
  (:require [ring.util.mime-type :refer [default-mime-types]])
  (:require [ring.util.response :refer [url-response content-type]])

  (:require [clojure.java.jdbc :as db])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [honeysql.core :as sql])
  (:require [honeysql.helpers :as h])
  (:require [promestein.utils :refer :all])

  (:require [clojure.tools.logging :as log])
  (:require [hiccup.core :refer [html]])
  (:require [promestein.jmdict :as jmdict]))

(defn re-positions [m]
  (lazy-seq
   (when (.find m)
     (cons [(.start m) (.end m)]
           (re-positions m)))))

(defn extract-by
  ([tokens f]
   (extract-by tokens f nil))
  ([tokens f merge-results]
   (let [xs
         (->> tokens
              (group-by f)
              (map #(vector (-> % first) (->> % second (map :id))))
              (into {}))]

     (if (nil? merge-results)
       (do
         (when (-> xs count (not= 1))
           (log/warn "inconsistent tokens data" xs))
         (-> xs keys first))
       (-> xs keys merge-results)))))

(defn get-chunk-by-id [conn chunk-id]
  (let [query
        (->>
         (-> (h/select :text)
             (h/from :chunks)
             (h/where [:= :id chunk-id]))
         sql/format)]
    (->> (db/query conn query sqlite-options)
         first
         :text)))

(defn get-marked-tokens [conn]
  (let [query
        (->>
         (-> (h/select :id :chunk-id :offset :text :normal-form :info :dict-entry-id :sense-index)
             (h/from :tokens)
             (h/where
              [:and
               [:<> :dict-entry-id nil]
               [:> :dict-entry-id 0]
               [:<> :sense-index nil]])
             (h/order-by [:chunk-id :asc] [:offset :asc]))
         sql/format)]

    (->> (db/query conn query sqlite-options)
         (map #(update % :info json/decode true))
         (group-by #(vector (:dict-entry-id %) (:sense-index %))))))

(defn format-context [text offset length]
  (let [dels "。\n\r"
        [chunk-start chunk-end]
        (->> (re-positions (re-matcher (re-pattern (str "[^" dels "]+")) text))
             (drop-while #(-> % second (<= offset)))
             first)
        token-start (- offset chunk-start)
        token-end   (+ token-start length)]
    {:chunk (subs text chunk-start chunk-end)
     :start token-start
     :end   token-end}))

(defn format-gloss [{:keys [detector dict-db]} [dict-entry-id sense-index] normal-form]
  (let [filter-entry-by
        (if (-> detector (.hasKanji normal-form))
          jmdict/filter-entry-by-kanji
          jmdict/filter-entry-by-reading)]

    (->>
     (-> (jmdict/get-entry-by-id dict-db dict-entry-id)

         (filter-entry-by normal-form)
         (jmdict/format-entry)
         ;; TODO: filter by pos
         :sense
         (jmdict/filter-sense-by-lang #{"eng" "rus"}))
     (filter #(-> % :id (= sense-index)))
     first
     :gloss
     (group-by :lang)
     (map #(vector (->> % first) (->> % second (map :content)))))))

(defn format-token [{:keys [user-db] :as opts} [sense tokens]]
  (let [normal-form
        (-> tokens
            (extract-by :normal-form))

        pos
        (-> tokens
            (extract-by #(-> % :info :part-of-speech)))

        pronunciation
        (-> tokens
            (extract-by #(-> % :info :pronunciation)
                        #(str/join ", " %)))

        gloss (format-gloss opts sense normal-form)

        context
        (->> tokens
             (map #(format-context
                    (->> % :chunk-id (get-chunk-by-id user-db))
                    (->  % :offset)
                    (->  % :text count)))
             doall)]

    {:normal-form   normal-form
     :pronunciation pronunciation
     :gloss         gloss
     :context       context}))

(defn render-gloss [gloss]
  (html
   [:div.gloss
    (->> gloss
         (map #(-> [:p
                    [:span.lang (-> % first)]
                    [:span (->> % second (str/join ", "))]])))]))

(defn render-context [context]
  (html
   [:div.context
    (->> context
         (map (fn [{:keys [chunk start end]}]
                [:p
                 (subs chunk 0 start)
                 [:span.hl (subs chunk start end)]
                 (subs chunk end)])))]))

(defn render-token [{:keys [normal-form pronunciation gloss context]}]
  (->> [normal-form
        pronunciation
        (render-gloss gloss)
        (render-context context)]
       (str/join "|")))

(defn strip-nl [s] (str/replace s #"[\n\r]" " "))

(defn render-flashcards [{:keys [dict-db user-db pages-processor]}]
  (db/with-db-transaction [user-conn user-db]
    (let [opts
          {:user-db  user-conn
           :dict-db  dict-db
           :detector (-> pages-processor :text :detector)}]
      (->> (get-marked-tokens user-conn)
           (map (partial format-token opts))
           (doall)
           (map render-token)
           (map strip-nl)
           (str/join "\n")))))
