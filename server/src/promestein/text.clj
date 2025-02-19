(ns promestein.text
  (:import [com.google.cloud.translate Translate Translate$TranslateOption Translation TranslateOptions])
  (:import [com.google.auth.oauth2 ServiceAccountCredentials])
  (:import [com.atilika.kuromoji.unidic.kanaaccent Tokenizer Token])
  (:import [com.moji4j MojiDetector])
  (:require [clojure.java.io :as io])
  (:require [clojure.set :refer [rename-keys]])
  (:require [cheshire.core :as json])
  (:require [clojure.java.jdbc :as db])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [honeysql.core :as sql])
  (:require [honeysql.helpers :as h])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])
  (:require [promestein.jmdict :as jmdict]))

;;; Shared resources

(defn create-text-processor
  "Create text processor shared resources. Dictionary database is not considered owned."
  [& {:keys [google-credentials-path dict-db]}]
  (let [credentials (with-open [creds (io/input-stream google-credentials-path)]
                      (ServiceAccountCredentials/fromStream creds))
        translate-settings (->
                            (TranslateOptions/newBuilder)
                            (.setCredentials credentials)
                            (.build))]
    {:translate (.getService translate-settings)
     :tokenizer (Tokenizer.)
     :detector (MojiDetector.)
     :dict-db dict-db}))

(defn close-text-processor
  "Close text processor resources."
  [{:keys []}]
  nil)

;;; Text processing

(defn is-japanese-word
  "Check that given token is a Japanese word."
  [^MojiDetector detector {:keys [surface info]}]
  (and (not (= "補助記号" (-> info :part-of-speech first)))
       (or (.hasKanji detector surface)
           (.hasKana detector surface))))

(defn translate-texts
  "Given strings, translates them via Google Translation API."
  [translate from to texts]
  (->>
   [(when (some? from) (Translate$TranslateOption/sourceLanguage from))
    (Translate$TranslateOption/targetLanguage to)
    (Translate$TranslateOption/format "text")]
   (filter some?)
   into-array
   (.translate translate texts)
   (map
    (fn [^Translation translation]
      {:source-lang (.getSourceLanguage translation)
       :text (.getTranslatedText translation)}))))

(defn tokenize-text
  "Tokenize given text using Kuromoji and MojiDetector."
  [{:keys [^Tokenizer tokenizer ^MojiDetector detector]} ^String string]
  (->> (.tokenize tokenizer string)
       (map (fn [^Token token]
              {:offset         (.getPosition token)
               :surface        (.getSurface token)
               :info {:part-of-speech [(.getPartOfSpeechLevel1 token)
                                       (.getPartOfSpeechLevel2 token)
                                       (.getPartOfSpeechLevel3 token)]

                      ;; Token's contents in katakana (e.g. こんにちは -> コンニチハ)
                      :reading (.getKana token)

                      ;; Token's pronunciation in katakana + (e.g. こんにちは -> コンニチワ)
                      :pronunciation (.getPronunciation token)

                      ;; Normalized token's contents
                      :normal-form (.getWrittenBaseForm token)

                      ;; Normalized token's contents in kanji
                      :lemma (.getLemma token)

                      ;; Normalized token's contents in kana
                      :lemma-reading (.getKanaBase token)

                      :accent (.getAccentType token)}}))
       (filter #(is-japanese-word detector %))))

;;; State database

(defn create-text-db
  "Create text state database."
  ([db-spec]
   (create-text-db db-spec {}))
  ([db-spec create-options]
   (db/with-db-transaction [conn db-spec]
     (let [my-options (merge create-options sqlite-options)]
       (db/execute!
        conn (db/create-table-ddl
              :chunks
              [[:id          "integer primary key autoincrement"]
               [:text        "text not null unique"]
               [:translation "text not null"]]
              my-options))
       (db/execute!
        conn (db/create-table-ddl
              :tokens
              [[:id             "integer primary key autoincrement"]
               [:chunk-id       "integer not null references chunks(id)"]
               [:offset         "integer not null"]
               [:text           "text not null"]
               [:info           "text not null"]
               [:normal-form    "text not null"]
               [:dict-entry-id  "integer"]
               [:sense-index    "integer"]]
              my-options))
       (db/execute! conn "CREATE INDEX IF NOT EXISTS tokens_chunks ON tokens (chunk_id)")))))

;;; Fetching

(defn get-chunk-by-text
  "Find chunk id by the text."
  [user-conn text]
  (let [query (sql/format {:select [:id]
                           :from [:chunks]
                           :where [:= :text text]})]
    (->> (db/query user-conn query sqlite-options)
         first
         :id)))

(defn get-chunk
  "Gets chunk by id"
  [user-conn chunk-id]
  (let [chunk-query (sql/format {:select [:text :translation]
                                 :from [:chunks]
                                 :where [:= :id chunk-id]})]
    (when-let [chunk (first (db/query user-conn chunk-query sqlite-options))]
      (let [tokens-query (sql/format {:select [:id :offset :text :info :normal-form :dict-entry-id :sense-index]
                                      :from [:tokens]
                                      :where [:= :chunk-id chunk-id]})
            tokens (db/query user-conn tokens-query sqlite-options)]
        (assoc chunk :tokens (map (fn [tok] (update tok :info #(json/decode % true))) tokens))))))

;;; Processing

(def token-ignore-threshold 10)

(defn- tokenize-chunk
  "Tokenize a chunk, looking up dictionary entries. Returns list of records in partial `tokens` table format."
  [processor user-conn dict-conn text]
  (->>
   text
   (tokenize-text processor)
   (map (fn [{:keys [offset surface info]}]
          (let [query-translated
                (->>
                 (-> (h/select :id :dict-entry-id :sense-index)
                     (h/from :tokens)
                     (h/where
                      [:and
                       [:<> :dict-entry-id nil]
                       [:<> :sense-index   nil]
                       [:= :normal-form (-> info :normal-form)]])
                     (h/order-by [:id :desc])
                     (h/limit 1))
                 (sql/format))

                translation
                (->
                 (db/query user-conn query-translated sqlite-options)
                 first)

                query-ignored
                (->>
                 (-> (h/select [:%count.* :count])
                     (h/from :tokens)
                     (h/where [:= :normal-form (-> info :normal-form)]))
                 (sql/format))

                ignored?
                (and
                 (nil? translation)
                 (< token-ignore-threshold
                    (->
                     (db/query user-conn query-ignored sqlite-options)
                     first :count (*))))]

            (let [entry-ids (jmdict/get-matching-entry-ids dict-conn (:lemma info))]
              (merge
               {:offset offset
                :text surface
                :info (json/encode info)
                :normal-form (-> info :normal-form)}
               (select-keys translation [:dict-entry-id :sense-index])
               (when ignored? {:dict-entry-id 0 :sense-index 1}))))))))

(defn process-chunks
  "Process chunks, possibly loading them into user database. Returns chunk ids."
  [{:keys [translate dict-db] :as processor} user-conn texts]
  (db/with-db-connection [dict-conn dict-db]
    (let [unique-texts (->> texts
                            (map-indexed #(hash-map %2 [%1]))
                            (apply merge-with concat)
                            (map (fn [[text indices]] {:indices indices :text text})))
          existing-chunk-ids (map #(get-chunk-by-text user-conn (:text %)) unique-texts)
          new-texts (keep-n (fn [entry chunk-id] (when (nil? chunk-id) entry))
                            unique-texts existing-chunk-ids)
          new-chunks-map (if (seq new-texts)
                           (let [translations (future (->>
                                                       new-texts
                                                       (map :text)
                                                       (translate-texts translate nil "en")
                                                       (map :text)))
                                 tokenized-texts (->>
                                                  new-texts
                                                  (map #(doall (tokenize-chunk processor user-conn dict-conn (:text %))))
                                                  doall)
                                 new-chunks (map (fn [{:keys [text]} translation]
                                                   {:text text
                                                    :translation translation})
                                                 new-texts @translations)
                                 new-chunk-ids (map (comp first vals)
                                                    (db/insert-multi! user-conn :chunks new-chunks sqlite-options))
                                 new-tokens (mapcat (fn [tokens chunk-id]
                                                      (map #(assoc % :chunk-id chunk-id) tokens))
                                                    tokenized-texts new-chunk-ids)]
                             (db/insert-multi! user-conn :tokens new-tokens sqlite-options)
                             (apply hash-map (mapcat (fn [{:keys [indices]} chunk-id] [indices chunk-id])
                                                     new-texts new-chunk-ids)))
                           [])
          combined-entries (->> (mapcat (fn [{:keys [indices]} chunk-id]
                                          (let [id (if (nil? chunk-id)
                                                     (get new-chunks-map indices)
                                                     chunk-id)]
                                            (map #(vector % id) indices)))
                                        unique-texts existing-chunk-ids)
                                (into {}))]
      (map-indexed (fn [index text] (get combined-entries index)) texts))))

(defn update-token [user-conn token-id token-data]
  (->>
   (-> (h/update :tokens)
       (h/where [:= :id token-id])
       (h/sset (-> token-data
                   (select-keys [:entry-id :sense-id])
                   (rename-keys {:entry-id :dict-entry-id
                                 :sense-id :sense-index}))))
   sql/format
   (db/execute! user-conn)))

(defn get-chunk-stats [user-conn chunk-id]
  (let [query
        (->>
         (-> (h/select [:%count.* :new])
             (h/from [:tokens :t])
             (h/where
              [:and
               [:= :t.chunk-id chunk-id]
               [:or
                [:= :t.dict-entry-id nil]
                [:= :t.sense-index nil]]])
             (h/group :t.chunk-id))
         (sql/format))]

    (->> (db/query user-conn query sqlite-options)
         first)))
