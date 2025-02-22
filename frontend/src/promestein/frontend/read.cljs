(ns promestein.frontend.read
  (:require-macros
   [secretary.core :refer [defroute]])
  (:require
   [reagent.core :as r]
   [reagent.session :as session]
   [promestein.frontend.ui.core
    :as ui
    :refer-macros [defcomponent]
    :refer [tr-extend set-current-page!]]
   [promestein.frontend.ui.cache :as cache :refer [lru-cache]]
   [promestein.frontend.common :refer [ref-config ref-backend-url with-ajax' with-image']]
   [promestein.frontend.components.text :as text]
   [promestein.frontend.components.image-map :as image-map]))

(tr-extend
 {:en
  {:read
   {:title "Read"}
   :watch
   {:title "Watch"}}})

(def pages-cache (lru-cache 1))

(defn with-pages-data [component]
  (with-ajax' component
    {:cache      pages-cache
     :preprocess (fn [_] {:path  "/pages/"})}
    nil))

(def ref-page-cache (cache/reactive lru-cache (r/cursor ref-config [:cache :pages])))

(defn with-page-data [component page-id]
  (with-ajax' component
    {:cache       @ref-page-cache
     :preprocess  (fn [page-id] {:path (str "/pages/" page-id)})
     :postprocess #(update % :type keyword)}
    page-id))

(def with-page-image
  (let [ref-cache (cache/reactive lru-cache (r/cursor ref-config [:cache :pages]))]
    (fn [component page-id]
      (with-image' component
        {:cache      @ref-cache
         :preprocess (fn [page-id] (str "/pages/" page-id "/image"))}
        page-id))))

(def ref-chunk-cache (cache/reactive lru-cache (r/cursor ref-config [:cache :chunks])))

(defn with-chunk-data [component chunk-id]
  (with-ajax' component
    {:cache      @ref-chunk-cache
     :preprocess (fn [chunk-id] {:path (str "/chunks/" chunk-id)})}
    chunk-id))

(defroute route-index "/read" []
  (cache/clear! pages-cache)
  (set-current-page! :read-index))

(defroute route-watch "/read/watch" []
  (set-current-page! :read-watch))

(defroute route-watch-chunk "/read/watch/:chunk-id" [chunk-id]
  (set-current-page! :read-watch {:chunk-id (int chunk-id)}))

(defroute route-page "/read/:page-id" [page-id]
  (set-current-page! :read-page {:page-id (int page-id)}))

(defroute route-page-chunk "/read/:page-id/:chunk-id" [page-id chunk-id]
  (set-current-page! :read-page {:page-id (int page-id) :chunk-id (int chunk-id)}))

(defn list-item-component [{:keys [id name plugin new]}]
  [:a.list-group-item.list-group-item-action
   {:href (route-page {:page-id id})}
   [:span name]
   [:span.float-right.badge.badge-secondary.badge-pill plugin]
   (when (and (some? new) (< 0 new))
     [:span.float-right.badge.badge-primary.badge-pill.mr-2 (str new)])])

(defn list-component [pages]
  [:div.container.mt-3
   [:div.list-group
    (->> pages
         reverse
         (map #(-> (list-item-component %) (with-meta {:key (:id %)}))))]])

(defmulti page-component (fn [params data] (-> data :type)))

(defmethod page-component :text [_ {:keys [name contents]}]
  [:div
   [:h2.mb-3 [:span name]]
   (let [page-id (session/get-in [:current-page :params :page-id])]
     [text/component
      {:config @ref-config
       :handlers
       {:on-translate
        (fn [_]
          (cache/invalidate! @ref-page-cache page-id))}}
      contents])])

(defn approx-surface
  "Calculates squared surface for a rectangle. Approximate because it's not
  strictly guaranteed text region would be a rectangle."
  [[p1 p2 p3 p4]]
  (let [x (- (:x p3) (:x p1))
        y (- (:y p3) (:y p1))]
    (+ (* x x) (* y y))))

(defn pagination-component [current-page-id mode pages]
  (let [link-component
        (fn [page-id & cs]
          [:div.col-lg-1.mx-0.px-0.text-center
           {:class (if (= page-id :watch) "col-1 mr-4" "col-2")}
           (when (some? page-id)
             (apply vector
                    :a.text-nowrap.text-secondary
                    {:style {:padding "0.5em"
                             :border-radius "100%"}
                     :href
                     (cond
                       (int? page-id)
                       (route-page {:page-id page-id})

                       (= :watch page-id)
                       (route-watch)

                       :otherwise "#")}
                    cs))])

        current-page  (->> pages (filter #(-> % :id (= current-page-id))) first)
        first-page-id (some-> pages first :id)
        last-page-id  (some-> pages last  :id)
        prev-page-id  (some->> pages (partition 2 1) (filter #(-> % second :id (= current-page-id))) first first  :id)
        next-page-id  (some->> pages (partition 2 1) (filter #(-> % first  :id (= current-page-id))) first second :id)]

    [:div.row.mb-4

     [link-component
      (when (not= current-page-id first-page-id) first-page-id)
      [:i.fa.fa-chevron-left] [:i.fa.fa-chevron-left]]

     [link-component prev-page-id  [:i.fa.fa-chevron-left.px-1]]

     [:div.col.text-center.text-truncate
      [:p.d-inline
       (condp = (or (-> @ref-config :pagination) :position)
         :position
         [:b (str (inc (count (take-while #(-> :id % (not= current-page-id)) pages))) " of " (count pages))]
         :name
         [:b (-> current-page :name)])]]

     [link-component next-page-id [:i.fa.fa-chevron-right.px-1]]

     [link-component
      (when (not= current-page-id last-page-id) last-page-id)
      [:i.fa.fa-chevron-right] [:i.fa.fa-chevron-right]]

     (when (-> mode (not= :watch))
       [link-component :watch [:i.far.fa-eye]])]))

(defmethod page-component :image  [{:keys [page-id chunk-id mode]} {:keys [contents] :as page}]
  (let [route-fn
        (condp = mode
          :read  route-page-chunk
          :watch route-watch-chunk
          (constantly nil))

        get-chunk-url
        (fn [id]
          (route-fn
           {:page-id  page-id
            :chunk-id id}))]

    [:div.container.mt-3
     [:div.row
      [:div.col-lg-6.mb-2
       (->
        [image-map/component
         {:handlers
          {:on-select-area
           (fn [id]
             (ui/redir (get-chunk-url id)))}
          :config @ref-config
          :default {:selected-area chunk-id}

          :map
          (->> contents
               :chunks
               (map #(hash-map
                      :id     (:chunk-id %)
                      :coords (:position %))))}]
        (with-page-image page-id))]

      [:div.col-lg-6
       (if chunk-id
         (-> [text/component
              {:config @ref-config
               :handlers
               {:on-translate
                (fn [_]
                  (cache/invalidate! @ref-chunk-cache chunk-id))}}]
             (with-chunk-data chunk-id))
         (if-let [new-chunk-id
                  (when-not chunk-id
                    (->> contents
                         :chunks
                         (sort-by #(-> % :length) >)
                         first
                         :chunk-id))]
           (ui/redir-component (get-chunk-url new-chunk-id) true)
           [:p.text-center.text-muted "(no area selected)"]))]]]))

(defcomponent read-index []
  (with-pages-data [list-component]))

(defcomponent read-page [{:keys [page-id] :as params}]
  [:div.container.mt-3
   (with-pages-data
     [pagination-component page-id :read])
   (->
    [page-component (merge params {:mode :read})]
    (with-page-data page-id))])

(defn watch-component [& _]
  (let [ref-state (r/atom {:page-id nil})]

    (r/create-class
     {:display-name "watch"

      :component-did-mount
      (fn [this & args]
        (let [[_ params] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (when-let [chunk-id (some-> params :chunk-id)]
            (swap! ref-state assoc :page-id :pending))))

      :component-did-update
      (fn [this _]
        (let [[_ params raw-event] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (if (some? raw-event)
            (let [event
                  (-> (.parse js/JSON raw-event)
                      (js->clj :keywordize-keys true)
                      (update :type keyword))]

              (prn "got event" params event)

              (condp = (:type event)
                :new-page
                (when (-> @ref-state :page-id (not= (:id event)))
                  (swap! ref-state assoc :page-id (:id event))
                  (cache/clear! pages-cache)
                  (ui/redir (route-watch)))

                (prn "unknown event type" (:type event))))
            nil)))

      :reagent-render
      (fn [params _]
        (let [{:keys [page-id]} @ref-state]
          (cond
            (nil? page-id)
            [ui/spinner-component]

            (= :pending page-id)
            (with-pages-data
              (fn [pages]
                (if-let [page-id (some-> pages last :id)]
                  (do
                    (swap! ref-state assoc :page-id page-id)
                    [ui/spinner-component])
                  [ui/redir-component (route-watch)])))

            :otherwise
            (-> [page-component (merge params {:mode :watch :page-id page-id})]
                (with-page-data page-id)))))})))

(defcomponent read-watch [params]
  [ui/with-sse
   [watch-component params]
   (str @ref-backend-url "/pages/events")])