(ns promestein.jmdict-test
  (:require [clojure.java.jdbc :as db])
  (:require (org.tobereplaced (mapply :refer [mapply])))
  (:require [mount.core :as mount :refer [defstate]])
  (:require [honeysql.core :as sql])
  (:require [clojure.java.io :as io])
  (:require [cheshire.core :as json])
  (:require [clojure.string :as str])
  (:require [clojure.set :as set])

  (:require [promestein.utils :refer :all])
  (:require [promestein.jmdict :refer :all])

  (:require [clojure.test :refer [deftest deftest- use-fixtures testing is]])
  (:require [clojure.test.check :as tc])
  (:require [clojure.test.check.generators :as gen])
  (:require [clojure.test.check.properties :as prop])
  (:require [clojure.test.check.clojure-test :refer [defspec]])

  (:gen-class))

(def num-tests 20)

;;; Utils

(defn fmap-with
  "Generates vector `[x (f x)]`, where `x` is generated from `gen`."
  [f gen]
  (gen/fmap #(vector % (f %)) gen))

(defn bind-with
  "Generates vector `[x y]`, where `x` is generated from `gen` and `y` is generated from (f x)."
  [f gen]
  (gen/bind gen (fn [x] (gen/fmap #(vector x %) (f x)))))

(defn vals-in
  "`get-in` combined with `vals` on steroids which returns list of matching values.
  `coll` is a hash map; `path` is a vector of keywords.
  * If a list of hash maps is provided instead as `coll`, results for all hash maps are combined;
  * You can use vector instead of a keyword in `path` to get all entries with matching keywords;
  * You can use a special keyword :* to get all values in a hash map."
  [coll path]
  (cond
    (or (empty? path) (nil? coll))
    coll

    (or (seq? coll) (vector? coll))
    (let [result (filter some? (map #(vals-in % path) coll))]
      (when (not (empty? result))
        (set result)))

    (map? coll)
    (let [[x & xs] path]
      (cond
        (= :* x)
        (recur (vals coll) xs)

        (or (seq? x) (vector? x))
        (let [result (filter some? (map #(vals-in coll (cons % xs)) x))]
          (set (apply concat result)))

        :else
        (recur (coll x) xs)))

    :else
    (throw (ex-info "Unknown case" {:coll coll :path path}))))

(defn filter-vals-strings
  "Filters strings in `vals-in` results that start with `str`."
  [coll str]
  (filter some? (map (fn [entry] (filter #(.startsWith % str) entry)) coll)))

(defn hash-map-comp
  "Like `comp` but merges hash maps of functions.
  For example, (def negative-quotient (hash-map-comp {:f -} {:f /}))."
  ([] {})
  ([x] x)

  ([lhs rhs]
   (merge lhs (into (hash-map) (for [[k rf] rhs]
                                 [k (if-let [lf (lhs k)] (comp lf rf) rf)]))))
  ([lhs rhs & rest]
   (reduce hash-map-comp (list* lhs rhs rest))))

;;; Database

(defstate dict-db
  :start (open-dict-db "data/JMdict.sqlite")
  :stop (close-database dict-db))

(defn gen-entry*
  "Get conforming entries from the dictionary (with IO).
  `hints` is a vector of strings that are are searched in entries."
  [& hints]
  (let [filters (when (not (empty? hints))
                  {:where
                   (->> hints
                        (map (fn [x] [:like :entry (str "%\"" (name x) "\"%")]))
                        (reduce (fn [a b] [:and a b])))})
        query (sql/format (conj
                           {:select [:id, :entry]
                            :from [:entries]
                            :order-by [:%random]
                            :limit 1}
                           filters))]
    (gen/fmap
     (fn [_] (if-let [{:keys [id entry]} (->> query
                                              (db/query dict-db)
                                              first)]
               (assoc (deserialize-entry entry) :id id)
               (throw (ex-info "No entries found" {:query query}))))
     (gen/return nil))))

;;; Tests

(deftest constraints-conform?-test
  (let [cs {"foo" ["foo"]
            "ba?" ["bar" "baz"]
            "*"   nil}]
    ;; initial data is ok
    (is (constraints-conform? ["foo" "bar" "baz"] (vals cs)))

    ;; ok not to have elems referred in a constraint
    (is (constraints-conform? ["bar" "baz"] (vals cs)))

    ;; ok to have elems referenced by at least one constraint
    (is (constraints-conform? ["foo" "bar"] (vals (select-keys cs ["foo" "ba?"]))))

    ;; not ok to have elems that are referenced by no constraint
    (is (not (constraints-conform? ["foo" "bar"] (vals (select-keys cs ["foo"])))))))

(defspec initial-entry-is-conform
  num-tests
  (prop/for-all* [(gen-entry*)] entry-conform?))

(defspec filtered-by-reading-entry-is-conform
  num-tests
  (prop/for-all
   [[e lemma] (->> (gen-entry*)
                   (bind-with #(->> % :r-ele keys gen/elements)))]
   (entry-conform? (filter-entry-by-reading e lemma))))

(defspec filtered-by-kanji-entry-is-conform
  num-tests
  (prop/for-all
   [[e lemma] (->> (gen-entry* :k-ele)
                   (bind-with #(->> % :k-ele keys gen/elements)))]
   (entry-conform? (filter-entry-by-kanji e lemma))))

(defspec filtered-by-pos-entry-is-conform
  num-tests
  (prop/for-all
   [[e pos] (->> (gen-entry* :pos)
                 (bind-with #(->> (vals-in % [:sense :pos]) (apply concat) gen/elements)))]
   (let [f  #(= pos %)
         e' (update e :sense (partial filter-sense-by-pos f))]
     (and
      (entry-conform? e')
      (is (vals-in e' [:sense :pos]) #{[pos]})))))

(defn formatted-prop
  [& {:keys [hints
             entry->sample
             entry->test
             sample->test]
      :or {entry->test  identity
           sample->test identity}}]
  (prop/for-all
   [[entry sample] (->> (gen/one-of (map #(apply gen-entry* %) hints))
                        (fmap-with entry->sample)
                        (gen/such-that (comp not empty? second)))]
   (let [entry-test  (entry->test entry)
         sample-test (sample->test sample)
         msg {:entry       entry
              :sample      sample
              :entry-test  entry-test
              :sample-test sample-test}]
     (is (= entry-test sample-test) msg))))

(defn formatted-kanji-prop [& {:as args}]
  (mapply formatted-prop
          (hash-map-comp
           {:sample->test set}
           args
           {:entry->sample #(vals-in % [:k-ele :*])
            :entry->test   #(-> % :k-ele format-kanji (vals-in [:*]))})))

(defn formatted-reading-prop [& {:as args}]
  (mapply formatted-prop
          (hash-map-comp
           {:sample->test set}
           args
           {:entry->sample #(vals-in % [:r-ele :*])
            :entry->test   #(-> % :r-ele format-reading (vals-in [:*]))})))

(defn formatted-sense-prop [& {:as args}]
  (mapply formatted-prop
          (hash-map-comp
           {:sample->test set}
           args
           {:entry->sample #(vals-in % [:sense])
            :entry->test   #(-> % :sense format-sense)})))

(defspec formatted-kanji-has-frequency
  num-tests
  (formatted-kanji-prop
   :hints [[:ke-pri :nf__]]
   :entry->sample (comp #(map first %) #(filter-vals-strings (vals-in % [:ke-pri]) "nf"))
   :entry->test   #(vals-in % [:frequency])
   :sample->test  (partial map format-frequency)))

(defspec formatted-kanji-has-info
  num-tests
  (formatted-kanji-prop
   :hints [[:ke-inf]]
   :entry->sample #(vals-in % [:ke-inf])
   :entry->test   #(vals-in % [:info])))

(defspec formatted-reading-has-frequency
  num-tests
  (formatted-reading-prop
   :hints [[:re-pri :nf__]]
   :entry->sample (comp #(map first %) #(filter-vals-strings (vals-in % [:re-pri]) "nf"))
   :entry->test   #(vals-in % [:frequency])
   :sample->test  (partial map format-frequency)))

(defspec formatted-sense-has-pos
  num-tests
  (formatted-sense-prop
   :hints [[:pos]]
   :entry->sample #(vals-in % [:pos])
   :entry->test   #(vals-in % [:pos])))

(defspec formatted-sense-has-field
  num-tests
  (formatted-sense-prop
   :hints [[:field]]
   :entry->sample #(vals-in % [:field])
   :entry->test   #(vals-in % [:field])))

(defspec formatted-sense-has-dialect
  num-tests
  (formatted-sense-prop
   :hints [[:dial]]
   :entry->sample #(vals-in % [:dial])
   :entry->test   #(vals-in % [:dialect])))

(defspec formatted-sense-has-antonym
  num-tests
  (formatted-sense-prop
   :hints [[:ant]]
   :entry->sample #(vals-in % [:ant])
   :entry->test   #(vals-in % [:antonym])))

(defspec formatted-sense-has-reference
  num-tests
  (formatted-sense-prop
   :hints [[:xref]]
   :entry->sample #(vals-in % [:xref])
   :entry->test   #(vals-in % [:reference])))

(defspec formatted-sense-has-misc
  num-tests
  (formatted-sense-prop
   :hints [[:misc]]
   :entry->sample #(vals-in % [:misc])
   :entry->test   #(vals-in % [:misc])))

(defspec formatted-sense-has-info
  num-tests
  (formatted-sense-prop
   :hints [[:s-inf]]
   :entry->sample #(vals-in % [:s-inf])
   :entry->test   #(vals-in % [:info])))

(defspec formatted-sense-has-source
  num-tests
  (formatted-sense-prop
   :hints [[:lsource :content] [:ls-wasei] [:ls-type]]
   :entry->sample #(vals-in % [:lsource])
   :entry->test   #(vals-in % [:source])
   :sample->test  (partial map #(map format-source %))))

(defspec formatted-sense-has-gloss
  num-tests
  (formatted-sense-prop
   :hints [[:gloss :content] [:g-type]]
   :entry->sample #(vals-in % [:gloss])
   :entry->test   #(vals-in % [:gloss])
   :sample->test  (partial map #(map format-gloss %))))

(use-fixtures
  :once
  (fn [f]
    (mount/start)
    (time (f))))
