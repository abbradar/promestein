(ns promestein.lib
  "Library functions without any dependencies.")

(set! *warn-on-reflection* true)

(defn map-values
  "Map values in a map."
  [f m]
  (into {} (for [[key value] m] [key (f key value)])))

(defn keep-values
  "Keep values in a map."
  [f m]
  (into {} (for [[key value] m
                 :let [r (f value)]
                 :when r]
             [key r])))

(defn keywordize-keys
  "Non-recursively transforms all map keys from strings to keywords."
  [m]
  (into {} (for [[key value] m] [(keyword key) value])))

(defn recur-keywordize-keys
  "Recursively transforms all map keys from strings to keywords."
  [m]
  (into {} (for [[key value] m]
             (let [new-value
                   (cond
                     (map? value)
                     (recur-keywordize-keys value)

                     (vector? value)
                     (into [] (map #(if (map? %) (recur-keywordize-keys %) %) value))

                     :else value)]
               [(keyword key) new-value]))))

(defn parse-int
  "Parse integer."
  [s]
  (Integer/parseInt s))

(defn keep-n
  "Returns a lazy sequence of the non-nil results of (f item1 item2...). Note,
  this means false return values will be included. f must be free of
  side-effects.  Returns a transducer when no collections are provided."
  [f & colls]
  (keep #(apply f %) (apply map list colls)))

(defn keep-indexed-n
  "Returns a lazy sequence of the non-nil results of (f index item1 item2...). Note,
  this means false return values will be included. f must be free of
  side-effects.  Returns a transducer when no collections are provided."
  [f & colls]
  (keep-indexed #(apply f %1 %2) (apply map list colls)))

(defn map-indexed-n
  "Returns a lazy sequence consisting of the result of applying f to 0
  and the first item of colls, followed by applying f to 1 and the second
  item in colls, etc, until colls are exhausted. Thus function f should
  accept 2+ arguments, index and items. Returns a stateful transducer when
  no collections are provided."
  [f & colls]
  (map-indexed #(apply f %1 %2) (apply map list colls)))

(defmacro def-
  "same as def, yielding non-public def"
  [name & decls]
  (list* `def (with-meta name (assoc (meta name) :private true)) decls))
