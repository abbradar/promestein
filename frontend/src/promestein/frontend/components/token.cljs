(ns promestein.frontend.components.token
  (:require
   ["wanakana"       :as wk]))

(defn related? [t t']
  (= (some-> t  :normal-form)
     (some-> t' :normal-form)))

(defn group-by-id [tokens]
  (->> tokens
       (group-by :id)
       (map (fn [[k v]] [k (first v)]))
       (into {})))

(defn format-ruby [format text]
  (let [dispatch-formatter
        (fn [fmt]
          (condp = fmt
            :hiragana wk/toHiragana
            :katakana wk/toKatakana
            :romaji   wk/toRomaji))]

    (->> format
         (map #((dispatch-formatter %) text))
         (remove nil?))))

(defn component
  [{:keys [handlers config] :as props}
   {:keys [id text info] :as token}]
  (let [color
        (cond
          (-> props :highlighted?) (-> config :color :token :highlighted)
          (-> props :related?)     (-> config :color :token :related)

          (and (-> token :dict-entry-id (= 0))
               (-> token :sense-index  (= 1)))
          (-> config :color :token :ignored)

          (and (-> token :dict-entry-id some?)
               (-> token :sense-index some?))
          (-> config :color :token :translated)

          :else
          (-> config :color :token :neutral))

        ruby-style
        {:font-size (-> config :ruby :size (* 0.01) (str "em"))}]

    (if (nil? id)
      text
      (let [pronunciation (:pronunciation info)
            subscripts (format-ruby (-> config :ruby :format) pronunciation)
            handler-attrs {:data-id (str id)
                           :on-mouse-over (-> handlers :on-mouse-over)
                           :on-mouse-out  (-> handlers :on-mouse-out)
                           :on-click      (-> handlers :on-click)}]
        (if (or false (empty? subscripts))
          [:span
           (merge handler-attrs {:style {:background color}})
           text]
          [:span
           {:style {:background color}}
           (let [inner [:ruby
                        handler-attrs
                        text
                        [:rt {:style ruby-style} (last subscripts)]]]
             (reduce (fn [t rt] [:ruby t [:rt {:style ruby-style} rt]]) inner (butlast subscripts)))])))))
