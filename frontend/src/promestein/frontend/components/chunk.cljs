(ns promestein.frontend.components.chunk
  (:require
   [reagent.core     :as r]
   [promestein.frontend.components.token :as token]))

(defn parse-int [s]
  (let [r (js/parseInt s)]
    (when (not (js/isNaN r)) r)))

(defn split-by-tokens
  "Returns `tokens` merged with additional elements, extracted from `s`."
  ([s tokens]
   (split-by-tokens s tokens 0))

  ([s tokens offset]
   (lazy-seq
    (if (empty? tokens)
      (when (< offset (count s))
        (->>
         nil
         (cons
          {:offset offset
           :text   (subs s offset (count s))})))

      (let [[t & tokens'] tokens]
        (if (= offset (-> t :offset))
          (->>
           (split-by-tokens s tokens' (+ (-> t :offset) (-> t :text count)))
           (cons t))

          (->>
           (split-by-tokens s tokens (-> t :offset))
           (cons
            {:offset offset
             :text   (subs s offset (-> t :offset))}))))))))

(defn component [& _]
  (let [ref-state
        (r/atom
         {:current-token-id nil})

        ref-current-token-id
        (r/cursor ref-state [:current-token-id])

        get-token-id
        (fn [e] (->> e .-target .-dataset .-id parse-int))

        on-mouse-over-token
        (fn [e]
          (reset! ref-current-token-id (get-token-id e)))

        on-mouse-out-token
        (fn [_]
          (reset! ref-current-token-id nil))

        on-click-token
        (fn [props e]
          (some-> props :handlers :on-select-token (apply (get-token-id e) ())))]

    (r/create-class
     {:display-name "chunk-component"

      :reagent-render
      (fn [{:keys [handlers config token-map] :as props} token-list]
        (let [{:keys [current-token-id]} @ref-state
              current-token (some->> current-token-id (get token-map))]
          [:div.chunk.noselect
           {:style
            {:line-height (-> config :ruby :format count (* 2 (-> config :ruby :size (* 0.01))) (+ 1) (str "em"))}}
           [:span.master.d-none
            {:style {:font-size "2em"}}]
           (->> token-list
                (map
                 #(let [token-data
                        (or
                         (when-let [id (-> % :id)]
                           (-> token-map (get id)))
                         %)]
                    (-> [token/component
                         {:related?     (token/related? token-data current-token)
                          :highlighted? (-> token-data :id (= current-token-id))

                          :config config

                          :handlers
                          {:on-mouse-over on-mouse-over-token
                           :on-mouse-out  on-mouse-out-token
                           :on-click      (partial on-click-token props)}}
                         token-data]
                        (with-meta {:key (:offset token-data)})))))]))})))
