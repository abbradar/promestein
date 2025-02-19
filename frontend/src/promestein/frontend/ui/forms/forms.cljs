(ns promestein.frontend.ui.forms.forms
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as r]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [promestein.frontend.ui.common :refer [spinner-component]]
   [promestein.frontend.ui.i18n :refer [tr]]
   [promestein.frontend.ui.forms.field :refer [field-component field-wrapper-component]]))

(defn- submit-button-component [{:keys [on-click label]}]
  [:button.btn.btn-primary.mt-4
   {:type "submit"
    :on-click on-click}
   (or label (tr :component/submit))])

(defn- form-contents-component [fields ref-value]
  [:div
   (->> fields
        (map #(-> [field-wrapper-component
                   (-> % (assoc :ref-value (r/cursor ref-value [(-> % :id keyword)])))]
                  (with-meta {:key (:id %)}))))])

(defn- dispatch-method [method]
  (condp = method
    :get     http/get
    :put     http/put
    :post    http/post
    :delete  http/delete))

(defn form-component [& _]
  (let [ref-state (r/atom nil)

        wait!
        (fn []
          (swap! ref-state #(-> %
                                (assoc :status :wait)
                                (dissoc :message))))]

    (r/create-class
     {:display-name "form"

      :reagent-render
      (fn [props & fields]
        (let [done!
              (fn [response]
                (let [message
                      (-> (:on-done props)
                          (#(cond
                              (string? %) %
                              (fn?     %) (% (-> response :body))))
                          (#(cond
                              (string? %) %
                              :else (tr :component.form/success))))]
                  (swap! ref-state
                         #(-> %
                              (assoc :status :done)
                              (assoc :message message)))))

              error!
              (fn [response]
                (let [message
                      (-> (:on-error props)
                          (#(cond
                              (string? %) %
                              (fn?     %) (% (-> response :status))))
                          (#(cond
                              (string? %) %
                              :else (tr :component.form/fail))))]
                  (swap! ref-state
                         #(-> %
                              (assoc :status :error)
                              (assoc :message message)))))

              preprocess
              (or (:preprocess props) identity)

              make-request
              (fn [{:keys [method url action params]}]
                ((dispatch-method method)
                 (or action url)
                 (merge
                  {:with-credentials? false}
                  (condp = (or (:content-type props) :json)
                    :json
                    {:json-params (preprocess @(:ref-value props))}

                    :multipart
                    {:multipart-params
                     (->> @(:ref-value props)
                          (mapcat (fn [[k v]] (if (coll? v) (map #(vector k %) v) [[k v]])))
                          (into []))})
                  params)))

              {:keys [labels ref-value validate postprocess ajax?] :or {ajax? true}} props
              {:keys [status message]} @ref-state]

          [:form.form
           (merge
            (-> props
                (dissoc :submit)
                (dissoc :labels)
                (dissoc :ref-value)
                (dissoc :content-type)
                (dissoc :validate)

                (dissoc :preprocess)
                (dissoc :postprocess)
                (dissoc :ajax?)

                (dissoc :on-done)
                (dissoc :on-error))

            {:on-submit
             (fn [e]
               (.preventDefault e)
               (let [value @ref-value
                     this  (-> e .-target)]

                 (when (or (nil? validate) (validate value))
                   (wait!)

                   (go
                     (let [err
                           (when postprocess
                             (let [response (<! (make-request postprocess))]
                               (if (:success response)
                                 (do
                                   (swap! ref-value update :hidden #(merge % (:body response)))
                                   nil)
                                 response)))]

                       (if (some? err)
                         (error! err)
                         (cond
                           (-> props :method nil?)
                           (done! nil)

                           ajax?
                           (let [response (<! (make-request props))]
                             (if (:success response)
                               (done! response)
                               (error! response)))

                           :else
                           ;;   v TODO: setTimeout is not reliable
                           (-> #(.submit this) (js/setTimeout 1000)))))))))})

           (when-let [header (:header labels)]
             [:h2.mb-4 header])

           [form-contents-component fields ref-value]

           (when-let [hidden-fields (-> @ref-value :hidden)]
             [:div
              (for [[k v] hidden-fields]
                (-> [:input {:type "hidden" :name (name k) :value v}]
                    (with-meta {:key (name k)})))])

           (when message
             (case status
               :done  (when message [:div.alert.alert-success [:p message]])
               :error (when message [:div.alert.alert-danger  [:p message]])
               nil))

           (if (= status :wait)
             [spinner-component]
             (when-let [submit (-> props :submit)]
               [field-component submit]))]))})))

(defn toggle-visibility [state & [values]]
  (-> (or state {}) (merge (or values {})) (update :visible? not)))

(defn toggle-visibility! [ref-state & [values]]
  (swap! ref-state #(toggle-visibility % values)))

(defn modal-form-component [{:keys [labels fields ref-value on-submit]}]
  (when (-> @ref-value (get :visible?))
    [:div.modal-form
     [:div.card.d-inline-block
      [:div.card-body
       [:a.align-top.float-right.text-secondary
        {:href "#"
         :on-click #(toggle-visibility! ref-value)}
        [:i.fas.fa-times.ml-1]]
       (when-let [header (:header labels)]
         [:h5.card-title header])
       [:hr]
       [form-contents-component fields ref-value]
       [:div.text-right
        [submit-button-component
         {:on-click #(on-submit ref-value)
          :label (:submit labels)}]]]]]))
