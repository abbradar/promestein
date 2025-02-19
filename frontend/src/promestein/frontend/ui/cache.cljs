(ns promestein.frontend.ui.cache
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [reagent.ratom :refer [reaction]])
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<!]]
   ["mnemonist/lru-map" :as LRUMap]))

(def pending-value :pending)

(defprotocol Cache
  (get!    [this key])
  (put!    [this key value])

  (clear! [this] [this key])
  (invalidate! [this key])

  (size        [this])
  (capacity    [this])
  (entries     [this]))

(defn lru-cache [n & [items]]
  (let [lru-map
        (if (some? items)
          (LRUMap/from items n)
          (LRUMap. n))

        raw-get!
        (fn [key]
          (when (-> lru-map (.has (str key)) not)
            (-> lru-map (.set (str key) (r/atom {}))))
          (-> lru-map (.get (str key))))]

    (reify
      Cache
      (get! [_this key]
        @(raw-get! key))

      (put! [_this key fetch]
        (let [ref-value (raw-get! key)]
          (when (-> @ref-value :locked? not)
            (swap! ref-value assoc :locked? true)
            (go
              (let [value (<! (fetch key))]
                (swap! ref-value
                       #(-> %
                            (assoc :value   value)
                            (assoc :locked? false)
                            (assoc :valid?  true))))))))

      (clear! [this]
        (doall (->> lru-map .keys es6-iterator-seq (map #(clear! this %)))))

      (clear! [_this key]
        (let [ref-value (raw-get! key)]
          (when (-> @ref-value :locked? not)
            (swap! ref-value
                   #(-> %
                        (assoc :valid? false)
                        (assoc :keep? false))))))

      (invalidate! [_this key]
        (let [ref-value (raw-get! key)]
          (when (-> @ref-value :locked? not)
            (swap! ref-value
                   #(-> %
                        (assoc :valid? false)
                        (assoc :keep? true))))))

      (size     [_this] (-> lru-map .-size))
      (capacity [_this] n)
      (entries  [_this] (-> lru-map .entries es6-iterator-seq)))))

(defn reactive [make-cache ref-capacity]
  (let [ref-cache (r/atom nil)]
    (reaction
     (let [capacity' (or @ref-capacity 1)]
       (cond
         (-> @ref-cache nil?)
         (reset! ref-cache (make-cache capacity'))

         (-> @ref-cache capacity (not= capacity'))
         (let [cache' (make-cache capacity' (->> @ref-cache entries (take capacity')))]
           (reset! ref-cache cache')))

       @ref-cache))))
