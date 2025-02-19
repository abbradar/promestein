(ns promestein.jmdict
  (:import [org.sqlite SQLiteConfig SQLiteDataSource])
  (:require [clojure.java.jdbc :as db])
  (:require [mount.core :as mount :refer [defstate]])
  (:require [honeysql.core :as sql])
  (:require [hikari-cp.core :as hikari])
  (:require [cheshire.core :as json])
  (:require [clojure.string :as str])
  (:require [clojure.set :as set :refer [rename-keys]])
  (:require [clojure.tools.trace :refer [trace]])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all]))

;;; To make more sense of this also read JMDict's DTD.

;;; Filtering

(defn- stag-match
  [stag-field xs v]
  (or (empty? xs)
      (let [ys (set (v stag-field))]
        (or (empty? ys)
            (not (empty? (set/intersection xs ys)))))))

(defn filter-sense-by-stags
  "Filter out sense entries whose `:stagk` and `:stagr` do not match `kebs` and `rebs` respectively."
  [sense kebs rebs]
  (let [k-check #(stag-match :stagk (set kebs) %)
        r-check #(stag-match :stagr (set rebs) %)]
    (filter #(and (k-check %) (r-check %)) sense)))

(defn filter-entry-by-reading
  "Filter out `kebs` that do not match `:re-restr` values of corresponding `lemma`."
  [{:keys [k-ele r-ele] :as entry} lemma]
  (let [r (get r-ele lemma)]
    (when r
      (let [re-restr (:re-restr r)]
        (update
         (merge entry
                {:k-ele (if re-restr (select-keys k-ele re-restr) k-ele)
                 :r-ele {lemma r}})
         :sense #(filter-sense-by-stags % re-restr [lemma]))))))

(defn- re-restr-match
  [keb v]
  (let [re-restr (set (:re-restr v))]
    (or (empty? re-restr) (contains? re-restr keb))))

(defn filter-entry-by-kanji
  "Filter out `rebs` whose `:re-restr` values do not match `lemma`."
  [{:keys [k-ele r-ele] :as entry} lemma]
  (let [k (get k-ele lemma)]
    (when k
      (let [r-ele (into {} (filter #(re-restr-match lemma (second %)) r-ele))]
        (update
         (merge entry
                {:k-ele {lemma k}
                 :r-ele r-ele})
         :sense #(filter-sense-by-stags % [lemma] (keys r-ele)))))))

(defn filter-sense-by-pos
  "Filter out sense entries by part of speech given a predicate function."
  [sense f]
  (filter #(if-let [pos (:pos %)]
             (->> pos (map f) (filter identity) empty? not)
             true)
          sense))

(defn filter-sense-by-lang [sense langs]
  (let [update-sense
        (fn [s]
          (if (-> s :gloss empty?)
            (assoc s :ignore? true)
            (update s :gloss (partial filter #(->> % :lang (contains? langs))))))]
    (->> sense
         (map update-sense)
         (filter #(or (-> % :ignore?) (-> % :gloss empty? not)))
         (map #(dissoc % :ignore?)))))

;;; Shared resources

(defn open-dict-db
  "Connect to existing dictionary database created by promestein-convert-dict."
  ([db-path]
   (open-dict-db db-path {}))
  ([db-path {:keys [trace] :or {trace false}}]
   (start-sqlite-database db-path {:read-only true :trace trace})))

(defn deserialize-entry
  "Convert serialized representation of an entry to the same format as in convert-dict."
  [entry-string]
  (let [e (json/decode entry-string)]
    (conj
     {:sense
      (->> (get e "sense")
           (map-indexed #(-> %2 recur-keywordize-keys (assoc :id (inc %1)))))
      :r-ele (map-values #(recur-keywordize-keys %2) (get e "r-ele"))}
     (when-let [k-ele (get e "k-ele")]
       {:k-ele (map-values #(recur-keywordize-keys %2) k-ele)}))))

(defn deserialize-db-rows [rows]
  "Deserialize [{:id, :entry}] into dictionary entries."
  (->>
   rows
   (map
    (fn [{:keys [id entry]}]
      (merge {:id id} (deserialize-entry entry))))
   (into [])))

(defn get-entities
  "Fetch map of xml-entities."
  [db-conn]
  (let [query (sql/format {:select [:name :value]
                           :from [:entities]})]
    (->> (db/query db-conn query (merge sqlite-options {:as-arrays? true}))
         rest
         (into {}))))

(defn get-matching-entry-ids
  "Fetch array of entry ids with `:keb` or `:reb` matching `lemma`."
  [db-conn lemma]
  (let [query (sql/format {:select [:entry-id]
                           :from [:readings]
                           :where [:= :reading lemma]})]
    (->> (db/query db-conn query sqlite-options)
         (map :entry-id))))

(defn get-entry-by-id
  "Fetch entry by id."
  [db-conn entry-id]
  (let [query (sql/format {:select [:entry]
                           :from [:entries]
                           :where [:= :id entry-id]})]
    (if-let [entry (first (db/query db-conn query sqlite-options))]
      (-> entry :entry deserialize-entry)
      nil)))

(defn get-entries-by-reading [db-conn reading]
  "Fetch array of entries with given `reading`."
  (let [query (sql/format {:select [:e.id :e.entry]
                           :from [[:readings :r]]
                           :join [[:entries :e] [:= :r.entry_id :e.id]]
                           :where [:= :reading reading]})]

    (->> (db/query db-conn query sqlite-options)
         deserialize-db-rows)))

;; TODO: obsolete?
(defn get-entries-by-normal-form
  [db-conn normal-form]
  (let [query (sql/format {:select [:entry]
                           :from [:readings]
                           :where [:= :normal-form normal-form]})]
    (->> (db/query db-conn query sqlite-options))))

(defn get-entry-refs [entry]
  "Get list of entries related to a given entry (both cross-references and antonyms)."
  (filter some? (mapcat #(concat (:ant %) (:xref %)) (:sense entry))))

;;; Formatting

(defn format-frequency [nfxx]
  (parse-int (subs nfxx 2)))

(defn- extract-frequency [xe-pri]
  (when-let [nfxx (first (filter #(str/starts-with? % "nf") xe-pri))]
    (format-frequency nfxx)))

(defn format-kanji [k-ele]
  (into {}
        (for [[k v] k-ele]
          (let [freq (extract-frequency (:ke-pri v))
                info (:ke-inf v)]
            [k (merge {}
                      (when freq {:frequency freq})
                      (when info {:info info}))]))))

(defn format-reading [r-ele]
  (into {}
        (for [[k v] r-ele]
          (let [freq (extract-frequency (:re-pri v))]
            [k (merge {}
                      (when freq {:frequency freq}))]))))

(defn format-gloss [{:keys [content lang g-type g-gend]}]
  (merge
   {}
   (when content {:content content})
   (when lang    {:lang    lang})
   (when g-type  {:type    g-type})
   (when g-gend  {:gender  g-gend})))

(defn format-source [{:keys [content lang ls-type ls-wasei]}]
  (merge
   {}
   (when content  {:content content})
   (when lang     {:lang    lang})
   (when ls-type  {:part    (= ls-type "part")})
   (when ls-wasei {:wasei   (= ls-wasei "y")})))

(defn format-sense [sense]
  (let [empty?' #(or (nil? %) (and (seq? %) (empty? %)))]
    (loop [xs sense
           state
           {:pos  nil
            :misc nil}
           result []]
      (if (empty? xs)
        result
        (let [[x & xs'] xs
              state'    (merge state (select-keys x [:pos :misc]))]
          (recur
           xs'
           state'
           (->>
            (-> x
                (rename-keys
                 {:s-inf   :info
                  :ant     :antonym
                  :xref    :reference
                  :dial    :dialect
                  :lsource :source})
                (update :source #(map format-source %))
                (update :gloss  #(map format-gloss  %))
                (merge state'))
            (filter #(-> % second empty?' not))
            (into {})
            (conj result))))))))

(defn format-entry [e]
  (-> e
      (update :k-ele format-kanji)
      (update :r-ele format-reading)
      (update :sense format-sense)))

;;; Predicates

(defn constraints-conform? [elems cs]
  "Check whether `elems` conforms given `cs`. More precisely:
   * each value of `elems` should be referenced by at least one of `cs`
   * `nil` in `cs` acts as a wildcard (i.e. references all values of `elems`)"

  (let [constrained? (->> cs (filter nil?) empty?)
        constraint   (->> cs (filter some?) (map set) (apply set/union))]
    (or (not constrained?)
        (empty? (set/difference (set elems) constraint)))))

(defn re-restr->kebs-conform? [{:keys [k-ele r-ele]}]
  (constraints-conform?
   (->> k-ele keys set)
   (->> r-ele vals (map :re-restr))))

(defn stagx->xeb-comform? [x {:keys [sense] :as entry}]
  (constraints-conform?
   (->> (keyword (str x "eb")) entry keys)
   (->> sense (map (keyword (str "stag" x))))))

(defn entry-conform? [e]
  (and (re-restr->kebs-conform? e)
       (stagx->xeb-comform? "k" e)
       (stagx->xeb-comform? "r" e)))

(defn match-pos-unidic-jmdict [unidic-pos jmdict-pos]
  (case [unidic-pos jmdict-pos]
    ;; TODO: provide complete matching
    ;; Example:
    ;; [["接続詞" "*" "*"] "conj"]        true
    ;; [["接続詞" "*" "*"] "n"]           false

    true))
