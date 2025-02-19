(ns promestein.frontend.ui.forms.field
  (:require
   [reagent.core :as r]
   [goog.string :as gs]
   [promestein.frontend.ui.i18n :refer [tr tr-extend]]
   [promestein.frontend.ui.forms.common :refer [parse-int parse-float]]))

(tr-extend
 {:en
  {:component
   {:not-supported "Field of type %s is not supported"
    :submit        "Submit"
    :form
    {:success "Success"
     :fail    "Failed"}
    :image
    {:fetch-url   "Fetch URL"
     :upload-file "Upload file"
     :success     "Successfully uploaded image"
     :fail        "Failed to get image"}}}})

(defmulti  field-component #(:type %))
(defmethod field-component :default [{:keys [type]}]
  [:p "[" (gs/format (tr :component/not-supported) type) "]"])

(defn field-head-component [{:keys [id label description]}]
  [:label {:for id}
   [:span.font-weight-bold label]
   [:br]
   [:span.pb-2.text-muted (or description "")]])

(defn field-wrapper-component [{:keys [preview ref-value] :as field}]
  [:div.pb-4
   [field-head-component field]
   [field-component field]
   (when preview
     [preview @ref-value])])

(defmethod field-component :text-short [{:keys [id ref-value]}]
  [:input.form-control
   {:id        id
    :value     @ref-value
    :type      "text"
    :on-change #(reset! ref-value (-> % .-target .-value))}])

(defmethod field-component :password [{:keys [id ref-value]}]
  [:input.form-control
   {:id        id
    :value     @ref-value
    :type      "password"
    :on-change #(reset! ref-value (-> % .-target .-value))}])

(defmethod field-component :text-long [{:keys [id ref-value]}]
  [:textarea.form-control
   {:id        id
    :value     @ref-value
    :on-change #(reset! ref-value (-> % .-target .-value))}])

(defmethod field-component :integer [{:keys [id ref-value]}]
  [:input.form-control
   {:id        id
    :value     (str @ref-value)
    :type      "text"
    :on-change #(when-let [value (-> % .-target .-value parse-int)]
                  (reset! ref-value value))}])

(defmethod field-component :range [{:keys [id ref-value min max step unit]}]
  [:div.form-group
   [:label {:for id} @ref-value unit]
   [:input.form-control-range
    {:id   id
     :type "range"
     :min min
     :max max
     :step step
     :value @ref-value
     :on-change (fn [e] (reset! ref-value (-> e .-target .-value parse-float)))}]])

(defmethod field-component :switch [{:keys [id ref-value labels]}]
  [:div.custom-control.custom-switch
   [:input.custom-control-input
    {:id id
     :type "checkbox"
     :default-checked @ref-value
     :on-change
     #(reset! ref-value (-> % .-target .-checked))}]

   [:label.custom-control-label.unselectable
    {:for id}
    (get labels (if @ref-value :on :off))]])

(defmethod field-component :color [{:keys [id ref-value]}]
  [:input.form-control.d-inline-block.p-0
   {:id        id
    :style
    {:width  "2em"
     :height "2em"}
    :value     @ref-value
    :type      "color"
    :on-change #(reset! ref-value (-> % .-target .-value))}])

(defmethod field-component :submit [{:keys [label] :as props}]
  [:button.btn
   (merge
    {:type "submit"}
    (-> props
        (dissoc :label)))
   (or label (tr :component/submit))])

(defmethod field-component :hash-map [{:keys [id ref-value fields align]}]
  (when (-> @ref-value nil?) (reset! ref-value {}))
  (let [format-field
        (fn [[field-id field]]
          (-> field
              (assoc :ref-value (r/cursor ref-value [(keyword field-id)]))
              (assoc :id (keyword (str (name id) "-" (name field-id))))))]
    [:div.pl-2
     (condp = align
       :vertical
       [:table.table
        [:tbody
         (->> fields
              (map format-field)
              (map #(-> [:tr
                         [:td.border-0.w-25 [field-head-component %]]
                         [:td.border-0 [field-component %]]]
                        (with-meta {:key (:id %)}))))]]
       :horizontal
       (->> fields
            (map format-field)
            (map #(-> [:div.d-inline-block.text-center.mr-4
                       [field-head-component %]
                       [:br]
                       [field-component %]]
                      (with-meta {:key (:id %)}))))
       (->> fields
            (map format-field)
            (map #(-> [field-wrapper-component %]
                      (with-meta {:key (:id %)})))))]))
