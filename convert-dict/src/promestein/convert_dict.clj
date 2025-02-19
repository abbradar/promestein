(ns promestein.convert-dict
  (:import [java.util.zip GZIPInputStream])
  (:import [javax.xml.parsers DocumentBuilderFactory])
  (:import [org.w3c.dom Entity NamedNodeMap EntityReference])

  (:require [clojure.data.xml :as xml])
  (:require [clojure.java.io :as io])
  (:require [clojure.java.jdbc :as db])
  (:require [clojure.string :as str])
  (:require [clojure.core.match :refer [match]])
  (:require [clojure.set :refer [rename-keys]])
  (:require [cheshire.core :as json])
  (:require [mount.core :as mount :refer [defstate]])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])

  (:gen-class))

(defn- content-to-string
  "Given XML content returns its string representation or null if no content is found.
  Also converts entity references to just text values."
  [content]
  (apply str
         (for [node content
               :let [repr (cond
                            (string? node)
                            node
                            (keyword? node)
                            (name node))]
               :when (some? repr)]
           repr)))

(defn- fixup-attribute-name
  "Kebab-caseify and remove namespace from an attribute name."
  [attr]
  (let [fixed-attr (if (= attr :xml:lang) :lang attr)]
    (keyword (from-json-keyword (name fixed-attr)))))

(defn- fixup-keys
  "Rename all keyword keys in map with fixup-attribute-name."
  [m]
  (into {} (for [[k v] m] [(fixup-attribute-name k) v])))

(defn- xml-to-entry
  "Converts XML entry to a simplified representation.
  XML element should:
  * Either contain only children elements with the same structure;
  * Or only text nodes
  Also fixes up attribute names."
  [{:keys [tag attrs content]}]
  (let [str (content-to-string content)
        value (if (empty? str)
                (map-values #(map xml-to-entry %2) (group-by :tag content))
                str)]
    (if (empty? attrs)
      value
      (into {:content value} (for [[k v] attrs] [(fixup-attribute-name k) v])))))

(defn- keyify-entries
  "Convert sequence of maps into a map, keyed by entries specified by `tag`."
  [tag elements]
  (->>
   elements
   (mapcat (fn [entry]
             (let [key (first (tag entry))
                   new-entry (dissoc entry tag)]
               [key new-entry])))
   (apply hash-map)))

(defn- parse-xref
  "Parse xref entry from JMDict into map."
  [raw]
  (match (str/split raw #"・")
    [target] {:target target}
    [target smth] (try
                    {:target target :sense (parse-int smth)}
                    (catch NumberFormatException e {:target target :reb smth}))
    [keb reb sense] {:target keb :reb reb :sense (parse-int sense)}
    :else (throw (ex-info "Invalid xref entry" {:entry raw}))))

(defn- parse-xml
  "Parse XML while avoiding entity expansion."
  [stream]
  (xml/parse stream
             :coalescing                  false
             :replacing-entity-references false))

(defn- format-sense [s]
  (cond-> (fixup-keys s)
    (:xref s) (update :xref #(map parse-xref %))
    (:ant s)  (update :ant  #(map parse-xref %))))

(defn- format-entry
  [e]
  (conj
   {:ent-seq (map parse-int (:ent_seq e))
    :sense (map format-sense (:sense e))
    :r-ele (keyify-entries :reb (map fixup-keys (:r_ele e)))}
   (when-let [k-ele (:k_ele e)]
     {:k-ele (keyify-entries :keb (map fixup-keys k-ele))})))

(defn load-dictionary-from-xml
  "Load JMDict dictionary from XML stream.
  Return reformatted dictionary with entries keyed by entry sequence numbers.

  The dictionary format changes as follows:
  * Tag and attribute names are kebab-ified;
  * k-ele and r-ele are dictionaries with kebs and rebs as corresponding keys;
  * xrefs are additionally parsed."
  [stream]
  (->> stream
       parse-xml
       :content
       (map xml-to-entry)
       (map format-entry)
       (keyify-entries :ent-seq)))

(defn load-entities-from-xml
  "Load JMDict entities from XML stream.
  Return entities keyed by entity keys."
  [stream]
  (let [builder (DocumentBuilderFactory/newInstance)
        ^NamedNodeMap entities (do
                                 (.setValidating builder true)
                                 (->
                                  builder
                                  .newDocumentBuilder
                                  (.parse stream)
                                  (.getDoctype)
                                  (.getEntities)))]
    (into {}
          (for [i (range 0 (.getLength entities))
                :let [^Entity entity (cast Entity (.item entities i))
                      name (.getNodeName entity)
                      text (.getTextContent entity)]
                :when (not (empty? text))]
            [name text]))))

(defn dictionary-index
  "Build JMDict index - pairs with readings and sequence numbers."
  [entries]
  (mapcat (fn [[key entry]]
            (let [maybe-keys (fn [tag] (if-let [inner (tag entry)] (keys inner)))]
              (map (fn [reading] [reading key]) (mapcat maybe-keys [:k-ele :r-ele]))))
          entries))

(defn create-db-dictionary
  "Fill database with JMDict, including an index."
  [db entries entities]
  (db/with-db-transaction [conn db]
    (db/execute! conn (db/create-table-ddl :entries
                                           [[:id "integer primary key"]
                                            [:entry "text not null"]] sqlite-options))
    (db/execute! conn (db/create-table-ddl :entities
                                           [[:name "text primary key"]
                                            [:value "text not null"]] sqlite-options))
    (db/execute! conn (db/create-table-ddl :readings
                                           [[:reading "text not null"]
                                            [:entry_id "integer not null references entries(id)"]] sqlite-options))
    (db/execute! conn "CREATE UNIQUE INDEX readings_unique ON readings (reading, entry_id)")

    (db/insert-multi! conn :entries
                      (map (fn [[id entry]]
                             {:id id
                              :entry (json/encode entry)})
                           entries) sqlite-options)

    (db/insert-multi! conn :entities
                      (map (fn [[name value]]
                             {:name name
                              :value value})
                           entities) sqlite-options)

    (db/insert-multi! conn :readings
                      (map (fn [[reading id]] {:reading reading :entry_id id})
                           (dictionary-index entries)) sqlite-options)

    nil))

(def input-path (nth *command-line-args* 0 "data/JMdict.gz"))
(def output-path (nth *command-line-args* 1 "data/JMdict.sqlite"))

(defstate entities
  :start (with-open [xml (GZIPInputStream. (io/input-stream input-path))]
           ;; Needed because JMDict has too many entity expansions,
           ;; even when expansion is disabled.
           (System/setProperty "jdk.xml.entityExpansionLimit" "0")
           (load-entities-from-xml xml)))

(defstate dictionary
  :start (with-open [xml (GZIPInputStream. (io/input-stream input-path))]
           ;; Needed because JMDict has too many entity expansions,
           ;; even when expansion is disabled.
           (System/setProperty "jdk.xml.entityExpansionLimit" "0")
           (load-dictionary-from-xml xml)))

(defn -main
  []
  (mount/start)
  (io/delete-file output-path true)
  (let [db {:classname "org.sqlite.JDBC"
            :subprotocol "sqlite"
            :subname output-path}]
    (create-db-dictionary db dictionary entities)))
