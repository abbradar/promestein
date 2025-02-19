(ns promestein.frontend.ui.forms.tags
  (:require
   [promestein.frontend.ui.forms.field :refer [field-component]]
   [clojure.string :as str]
   [reagent.core :as r]))

(defmethod field-component :tags [_]
  (let [ref-state (r/atom {})
        add-tag
        (fn [tag tags]
          (let [tags' (or tags [])]
            (swap! ref-state #(-> % (dissoc :raw) (dissoc :suggestions)))
            (if (= -1 (.indexOf tags' tag))
              (conj tags' tag)
              tags')))

        del-tag
        (fn [tag tags]
          (->> (or tags [])
               (filter (fn [v] (not (= tag v))))
               (into [])))

        make-suggestions
        (fn [tag used-tags dictionary]
          (let [used-tags' (into #{} (map str/lower-case used-tags))]
            (when (seq tag)
              (->> dictionary
                   (filter #(str/includes? (str/lower-case %) (str/lower-case tag)))
                   (filter #(not (contains? used-tags' (str/lower-case %))))
                   (sort-by str/lower-case)))))

        on-change
        (fn [{:keys [ref-value limit dictionary]} _state value s]
          (if (empty? s)
            (when (seq value)
              (swap! ref-value pop))
            (when (or (nil? limit) (< (count value) limit))
              (let [[_ raw' end] (re-matches #"^[^#]*#+ *([^ #]*)([ #]?)$" s)]
                (if (empty? end)
                  (swap! ref-state #(-> %
                                        (assoc :raw raw')
                                        (assoc :suggestions (make-suggestions raw' value dictionary))))
                  (swap! ref-value (partial add-tag raw')))))))

        tag-component
        (fn [{:keys [ref-value]} tag]
          [:button.btn.btn-primary.d-inline.font-weight-bold
           {:on-click #(swap! ref-value (partial del-tag tag))}
           "#" tag])

        entry-component
        (fn [{:keys [ref-value]} tag]
          [:a.list-group-item.list-group-item-action
           {:href "#"
            :on-click #(swap! ref-value (partial add-tag tag))}
           tag])]

    (r/create-class
     {:display-name "tags"
      :reagent-render
      (fn [{:keys [id ref-value limit] :as field}]
        (let [{:keys [raw suggestions] :as state} @ref-state
              value @ref-value]
          [:div.tags
           [:div.input-group
            [:div.input-group-prepend
             (map-indexed #(-> (tag-component field %2) (with-meta {:key %1})) value)]
            [:input.form-control.font-weight-bold
             {:id id
              :type "text"
              :value (str "#" raw)
              :class (if (or (nil? limit) (< (count value) limit))
                       "text-primary"
                       "text-muted")
              :on-key-down (fn [e]
                             (when (and (-> e .-which (= 13))
                                        (seq raw))
                               (swap! ref-value (partial add-tag raw))))
              :on-blur   #(when (empty? suggestions)
                            (on-change field state value (str (-> % .-target .-value) " ")))

              :on-change #(on-change field state value (-> % .-target .-value))}]]

           (when (seq suggestions)
             [:div.list-group.position-absolute.z-index-dropdown
              (->> suggestions
                   (map #(-> (entry-component field %)
                             (with-meta {:key %}))))])]))})))
