(ns promestein.frontend.ui.forms.select
  (:require
   [promestein.frontend.ui.forms.field :refer [field-component]]
   [promestein.frontend.ui.i18n :refer [tr tr-extend]]))

(tr-extend
 {:en
  {:component
   {:select
    {:choose "Choose..."}}}})

(defmethod field-component :select [{:keys [id ref-value choices]}]
  [:select.form-control
   {:id id
    :selected @ref-value
    :on-input #(reset! ref-value (-> % .-target .-value keyword))}

   (->> choices
        (map #(let [{:keys [value label]} %]
                (-> [:option {:value value} label]
                    (with-meta {:key value})))))])

(defmethod field-component :multi-select [{:keys [ref-value collection-type choices]}]
  (let [add-value!
        (fn [value]
          (swap! ref-value
                 #(-> (into collection-type %)
                      (conj value))))
        del-value!
        (fn [value]
          (swap! ref-value
                 #(-> (into #{} %)
                      (disj value)
                      (into collection-type))))

        choice-component
        (fn [value]
          [:button.btn.btn-outline-secondary.d-inline
           {:type "button"
            :on-click (fn [_] (del-value! value))}
           (->> choices (filter #(-> % :value (= value))) first :label)])

        option-component
        (fn [{:keys [value label]}]
          [:option {:value value} label])]

    [:div.input-group.mb-3
     [:div.input-group-prepend
      (map #(-> % choice-component (with-meta {:key %})) @ref-value)]

     (let [selected (->> @ref-value (into #{}))
           choices' (->> choices (filter #(->> % :value (contains? selected) not)))]

       (when (seq choices')
         [:select.custom-select
          {:default ""
           :on-input
           (fn [e]
             (let [value (-> e .-target .-value)]
               (when (seq value)
                 (add-value! (keyword value)))))}
          [:option {:value ""} (tr :component.select/choose)]
          (map #(-> % option-component (with-meta {:key (:value %)})) choices')]))]))
