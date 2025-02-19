(ns promestein.frontend.components.text
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core     :as r]
   [cljs.core.async  :refer [<!]]
   [cljs-http.client :as http]
   [promestein.frontend.ui.core :as ui]
   [promestein.frontend.components.token :as token]
   [promestein.frontend.components.chunk :as chunk]
   [promestein.frontend.components.dictionary :as dictionary]))

(defn component [& _]
  (let [ref-state
        (r/atom
         {:token-list     nil
          :token-map      nil
          :selected-token nil
          :locked?       nil})

        ref-selected-token
        (r/cursor ref-state [:selected-token])

        ref-locked?
        (r/cursor ref-state [:locked?])

        on-update
        (fn [{:keys [config]} {:keys [text tokens]}]
          (swap! ref-state
                 (fn [s]
                   (-> s
                       (assoc :token-list
                              (cond->> (chunk/split-by-tokens text tokens)
                                (-> config :chunk :strip-empty-tokens)
                                (filter #(->> % :text (re-matches #" *") not))))
                       (assoc :token-map  (token/group-by-id tokens))))))

        set-selected-token!
        (fn [token-id]
          (reset! ref-selected-token token-id))

        clear-selected-token!
        (fn []
          (reset! ref-selected-token nil))]

    (r/create-class
     {:display-name "text-component"

      :component-did-mount
      (fn [this]
        (let [[_ props contents] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (on-update props contents)))

      :component-did-update
      (fn [this old-argv]
        (let [[_ props  contents]  old-argv
              [_ props' contents'] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (when (or (not= contents contents')
                    (not= (some-> props  :config :chunk :strip-empty-tokens)
                          (some-> props' :config :chunk :strip-empty-tokens)))
            (on-update props' contents'))))

      :reagent-render
      (fn [{:keys [handlers config]} {:keys [translation]}]
        (let [{:keys [selected-token token-list token-map locked?]} @ref-state
              show-autotranslation (-> config :chunk :show-autotranslation)]
          [:div.text
           (when show-autotranslation [:p translation])

           [ui/cover-component
            (when selected-token
              [dictionary/component
               {:locked? locked?
                :config  config
                :handlers
                {:on-close clear-selected-token!
                 :on-select
                 (fn [{:keys [entry-id sense-id] :as params}]
                   (let [selected-token-data
                         (get token-map selected-token)
                         ids (conj
                              (->> token-list
                                   (map #(get token-map (:id %)))
                                   (filter #(token/related? % selected-token-data))
                                   (map :id)
                                   (filter #(not= % selected-token)))
                              selected-token)]

                     (go
                       (reset! ref-locked? true)
                       (let [response (<! (http/put (str (-> config :backend-url) "/tokens")
                                                    {:json-params
                                                     {:ids    ids
                                                      :params params}
                                                     :with-credentials? false}))]

                         (if (-> response :success)
                           (do
                             (some-> handlers :on-translate (apply selected-token ()))
                             (reset! ref-selected-token nil))

                           (prn :error response))

                         (reset! ref-locked? false)))))}}
               (get token-map selected-token)])]

           [chunk/component
            {:token-map token-map
             :config    config
             :handlers
             {:on-select-token set-selected-token!}}
            token-list]

           (when (not show-autotranslation)
             [ui/spoiler-component {:config (-> config :color :spoiler)} [:p translation]])]))})))
