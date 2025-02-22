(ns promestein.frontend.upload
  (:require-macros
   [promestein.frontend.ui.core :refer [defcomponent]]
   [secretary.core :refer [defroute]])
  (:require
   [reagent.session :as session]
   [promestein.frontend.ui.core
    :as ui
    :refer [tr tr-extend set-current-page!]]
   [promestein.frontend.ui.cache :as cache]
   [promestein.frontend.common :refer [ref-backend-url]]
   [promestein.frontend.read :as read]))

(tr-extend
 {:en
  {:upload
   {:title "Upload"
    :text  "Text"
    :image "Image"
    :submit "Submit"}}})

(defroute "/upload" []
  (set-current-page! :upload))

(defroute "/upload/:type" [type]
  (set-current-page! :upload {:type (keyword type)}))

(defmulti upload-component identity)

(defn on-done [ids]
  (cache/clear! read/pages-cache)
  (ui/redir (str "/read/" (some-> ids first :id))))

(defmethod upload-component :text []
  (when-let [backend-url @ref-backend-url]
    [ui/form-component
     {:method :post
      :action (str backend-url "/pages/new/text")
      :content-type :multipart

      :ref-value (session/cursor [:state :upload :text :form])

      :validate
      (fn [{:keys [text]}]
        (-> text empty? not))

      :on-done on-done

      :submit
      {:type :submit
       :label (tr :upload/submit)
       :class ["btn-primary"]}}

     {:id :name
      :label "Name"
      :type :text-short}

     {:id :text
      :label "Text"
      :type :text-long}]))

(defmethod upload-component :image []
  (when-let [backend-url @ref-backend-url]
    [ui/form-component
     {:method :post
      :action (str backend-url "/pages/new/image")
      :content-type :multipart

      :ref-value (session/cursor [:state :upload :image :form])

      :validate
      (fn [{:keys [image]}]
        (-> image nil? not))

      :on-done on-done

      :submit
      {:type :submit
       :label (tr :upload/submit)
       :class ["btn-primary"]}}

     {:id :image
      :label "Image"
      :type :image-basic
      :multiple true}]))

(defcomponent upload [{:keys [type] :as params}]
  (let [items [:text :image]

        selected-type (or type (-> items first))

        item-component
        (fn [item]
          [:li.nav-item
           [:a.nav-link
            {:class (when (-> item (= selected-type)) "active")
             :href (str "/upload/" (-> item name))}
            (->> item name (str "upload/") keyword tr)]])]

    [:div.intro.mt-3
     [:div.container
      [:ul.nav.nav-tabs.mb-3
       (->> items
            (map #(-> [item-component %] (with-meta {:key %}))))]
      [upload-component selected-type]]]))
