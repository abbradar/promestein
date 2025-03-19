(ns promestein.frontend.ui.forms.image
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as r]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [promestein.frontend.ui.i18n :refer [tr]]
   [promestein.frontend.ui.common :refer [spinner-component]]
   [promestein.frontend.ui.forms.field :refer [field-component]]
   [clojure.contrib.humanize :refer [filesize]]))

(defmethod field-component :image-basic [{:keys [id ref-value multiple] :or {multiple false}}]
  [:div
   [:label.btn.btn-primary
    {:for id}
    [:i.fas.fa-file-upload.mr-2]

    (let [[file & files] (cond-> @ref-value (not multiple) vector)]
      (cond
        (nil? file)
        (tr :component.image/upload-file)

        (empty? files)
        (str (-> file .-name)
             " (" (-> file .-size filesize) ")")

        :else
        (str (-> file .-name)
             " and " (count files) " more "
             " (" (->> files (map #(.-size %)) (apply +) filesize) ")")))]

   [:input.d-none
    {:id     id
     :type   "file"
     :accept "image/*"
     :multiple  multiple
     :on-change
     #(let [files (-> % .-target .-files array-seq)]
        (reset! ref-value
                (if multiple (into [] files) (first files))))}]])

(defn- request-file [{:keys [wait done error]} params]
  (wait)
  (go (let [response
            (<! (http/post "/api/upload" params))]
        (if (:success response)
          (done  (response :body))
          (error (response :error-text))))))

(defn- image-field-fetch-component
  [{:keys [id handlers ref-state]}]
  [:div.row
   [:div.col-auto
    [:button.btn.btn-primary.d-inline
     {:on-click
      #(when-let [url @ref-state]
         (request-file handlers {:query-params {:url url}}))}
     [:i.fas.fa-file-import.mr-2] (tr :component.image/fetch-url)]]
   [:div.col
    [:input.form-control.d-inline
     {:id (str id "-url")
      :type "text"
      :value @ref-state
      :on-change #(reset! ref-state (-> % .-target .-value))}]]])

(defn- image-field-upload-component
  [{:keys [id handlers]}]
  [:div
   [:label.btn.btn-primary
    {:for (str id "-file")} [:i.fas.fa-file-upload.mr-2] (tr :component.image/upload-file)]
   [:input.d-none
    {:id     (str id "-file")
     :type   "file"
     :accept "image/*"
     :on-change
     #(when-let [file (-> % .-target .-files array-seq first)]
        (request-file handlers {:multipart-params [["file" file]]}))}]])

(defmethod field-component :image [_]
  (let [ref-state (r/atom {})
        handlers
        (fn [{:keys [ref-value]}]
          {:wait
           (fn [] (swap! ref-state #(-> % (assoc :loading? true))))

           :done
           (fn [result]
             (swap! ref-state
                    #(-> %
                         (dissoc :loading?)
                         (assoc :message (tr :component.image/success))))
             (reset! ref-value result))

           :error
           (fn [text]
             (swap! ref-state
                    #(-> %
                         (dissoc :loading?)
                         (assoc :message (str (tr :component.image/fail) ": " text)))))})]

    (r/create-class
     {:display-name "image"
      :reagent-render
      (fn [{:keys [id ref-value simple?] :as field}]
        (let [{:keys [message loading?]} @ref-state
              value @ref-value]

          (if (some? value)
            [:div.image
             (when message [:div.alert.alert-success [:p message]])
             [:img {:src value}]
             [:a.align-top.text-secondary
              {:href "#"
               :on-click
               (fn [_]
                 (swap! ref-state #(-> % (dissoc :message)))
                 (reset! ref-value nil))}
              [:i.fas.fa-times.ml-1]]]

            (if loading?
              [spinner-component]
              [:div.image
               (when message [:div.alert.alert-danger [:p message]])
               [:div.row
                [:div.col-auto
                 [image-field-upload-component
                  {:id id
                   :handlers (handlers field)}]]
                [:div.w-100.d-sm-none]
                (when (not simple?)
                  [:div.col
                   [image-field-fetch-component
                    {:id id
                     :ref-state (r/cursor ref-state [:upload])
                     :handlers (handlers field)}]])]]))))})))
