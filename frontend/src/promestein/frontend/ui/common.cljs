(ns promestein.frontend.ui.common
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [reagent.ratom :refer [reaction]])
  (:require
   [reagent.core :as r]
   [cljs-http.client :as http]
   [cljs.core.async :refer [>! chan]]
   [reagent.session :as session]
   [promestein.frontend.ui.i18n :refer [tr tr-extend]]
   [promestein.frontend.ui.cache :as cache]))

(tr-extend
 {:en
  {:misc
   {:back "Return back"}
   :not-found
   {:header "Page not found"
    :message "The page you are looking for does not exist."}
   :forbidden
   {:header "Access denied"
    :message "You are not authorized to access this page."}}})

;; combinators
(defn intercalate [sep elems]
  (when (seq elems)
    (let [[e & es] elems]
      (->> es
           (mapcat
            #(-> sep
                 (with-meta {:key (when-let [key (-> % meta :key)] (str key "-sep"))})
                 (vector %)))
           (cons e)))))

(defn add-auth-token [token]
  {:headers
   {"Authorization" (str "Token " token)}})

(defn- apply' [f x]
  (cond
    (vector? f) (conj f x)
    (fn? f)     [f x]))

(defn with-placeholder [props component value]
  (let [success?          (-> props :success?          (or (constantly true)))
        loading-component (-> props :loading-component (or :div))
        error-component   (-> props :error-component   (or (constantly [:div])))
        postprocess       (-> props :postprocess       (or identity))]
    (cond
      (= value cache/pending-value) [loading-component]
      (success? value)              (apply' component (postprocess value))
      :else                         (apply' error-component value))))

(defn with-async-resource [component props f args]
  (let [fetch     (comp f (:preprocess props))
        cache     (:cache props)

        ref-value
        (reaction
         (let [{:keys [valid? locked? keep? value]} (cache/get! cache args)
               need-update? (and (not valid?) (not locked?))]

           (when need-update?
             (cache/put! cache args fetch))

           (cond
             keep?        value
             need-update? cache/pending-value
             valid?       value
             :else        cache/pending-value)))]

    (with-placeholder props component @ref-value)))

(defn with-ajax [component props args]
  (with-async-resource
    component
    (-> props
        (assoc  :success?    #(-> % (get :success) (= true)))
        (update :postprocess #(-> % (or identity) (comp :body))))
    #(http/get (:url %) (:params %))
    args))

(defn fetch-image [src]
  (let [c (chan)
        img (js/Image.)]
    (doto img
      (-> .-crossOrigin (set! "anonymous"))
      (-> .-onload
          (set! (fn []
                  (go (>! c img)))))
      (-> .-src (set! src)))
    c))

(defn with-image [component props src]
  (with-async-resource component props fetch-image src))

(defn with-sse
  "Component connecting to a Server Side Events endpoint and passing last event
  to a child component."
  [& _]
  (let [ref-state
        (r/atom {:last-event nil
                 :listener   nil})

        remove-listener!
        (fn [] (some-> @ref-state :listener .close))

        create-listener!
        (fn create-listener! [url]
          (swap! ref-state assoc :listener
                 (doto (js/EventSource. url)
                   (-> .-onmessage
                       (set! (fn [e] (swap! ref-state assoc :last-event (.-data e)))))
                   (-> .-onerror
                       (set! (fn [e]
                               (prn (str "Error receiving events from " url ": " e))
                               (remove-listener!)
                               (create-listener! url)))))))]

    (r/create-class
     {:display-name "sse-wrapper"

      :component-did-mount
      (fn [this]
        (let [[_ _ url] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (create-listener! url)))

      :component-will-unmount
      (fn [_this]
        (remove-listener!))

      :component-did-update
      (fn [this old-argv]
        (let [[_ _ new-url] (-> this .-props (js->clj :keywordize-keys true) :argv)
              [_ _ old-url] old-argv]
          (when (not= new-url old-url)
            (remove-listener!)
            (create-listener! new-url))))

      :reagent-render
      (fn [subcomponent _]
        (apply' subcomponent (-> @ref-state :last-event)))})))

;; utils
(defn link-back-component []
  [:a {:href "javascript:history.back()"}
   [:i.fas.fa-arrow-left.mr-2] (tr :misc/back)])

(defn with-link-back [component]
  [:div.mt-3
   [link-back-component]
   component])

(defn spinner-component []
  [:div.container.text-center.py-4
   [:div.spinner-border.text-primary {:role "status"} [:span.sr-only "Loading..."]]])

(defn cover-component [c]
  [:div
   (when (some? c)
     [:div
      {:style
       {:position "fixed"
        :top 0
        :left 0
        :width "100%"
        :height "100%"
        :z-index 1200}}

      [:div
       {:style
        {:position "fixed"
         :width "100%"
         :height "100%"
         :background "#FFF"
         :opacity 0.5}}]

      [:div
       {:style
        {:display "flex"
         :height "100%"
         :width "100%"
         :position "fixed"
         :flex-direction "column"
         :justify-content "center"}}
       c]])])

(defn spoiler-component [& _]
  (let [ref-state
        (r/atom
         {:spoilered? false
          :contents   nil})

        ref-spoilered? (r/cursor ref-state [:spoilered?])

        on-update
        (fn [contents]
          (reset! ref-state
                  {:spoilered? false
                   :contents   contents}))]

    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [[_ _ c] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (on-update c)))

      :component-did-update
      (fn [this old-argv]
        (let [[_ _ c]  old-argv
              [_ _ c'] (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (when (not= c c')
            (on-update c'))))

      :reagent-render
      (fn [props c]
        (let [{:keys [spoilered? contents]} @ref-state
              show (and spoilered? (= contents c))]
          [:div.spoiler
           (merge
            props
            {:on-click (fn [_] (reset! ref-spoilered? true))
             :style
             (if show
               {:background (-> props :config :background)}
               {:background (-> props :config :foreground)
                :cursor "help"})})
           [:div
            {:style (when (not show) {:visibility "hidden"})}
            c]]))})))

(defn lockable-component [c locked?]
  (if locked?
    [:div.position-relative
     [:div {:style {:opacity "0.75"}} c]
     [:div.position-absolute.w-100
      {:style {:top "50%"}}
      [spinner-component]]]
    c))

;; main

(defn header-item-component [{:keys [name label href]} page]
  [:li.nav-item
   (merge (when (= name page) {:class "active"}))
   [:a.nav-link {:href href} label]])

(defn header-dropdown-component [{:keys [label items]} page]
  [:li.nav-item.dropdown
   [:a.nav-link.dropdown-toggle
    {:href        "#"
     :role        "button"
     :data-toggle "dropdown"
     :class
     (when (->> items
                (filter #(-> % :name (= page)))
                seq)
       "active")}
    label]
   [:div.dropdown-menu
    (->> items
         (map #(-> [:a.dropdown-item
                    {:href  (:href %)
                     :class (when (-> % :name (= page)) "active")}
                    (:label %)]
                   (with-meta {:key (:name %)}))))]])

(defn header-item-list-component [items page opts]
  [:ul.navbar-nav.ml-4 opts
   (map #(-> (if (-> % :href some?)
               [header-item-component % page]
               [header-dropdown-component % page])
             (with-meta {:key (:name %)}))
        items)])

(defn header-component [{:keys [home menu controls]}]
  (let [page (session/get :current-page)]
    [:nav.navbar.navbar-expand-lg.navbar-dark.bg-dark
     [:div.container
      (when home
        [:a.navbar-brand {:href (:href home)} (:label home)])

      (when (or menu controls)
        [:button.navbar-toggler
         {:type "button"
          :data-toggle "collapse"
          :data-target "#navbarSupportedContent"}
         [:span.navbar-toggler-icon]])

      [:div#navbarSupportedContent.collapse.navbar-collapse
       (when menu     [header-item-list-component menu     page {:class "mr-auto"}])
       (when controls [header-item-list-component controls page {:class "my-auto"}])]]]))

(defn footer-component []
  [:div.container
   [:span.text-muted (tr :footer/all-rights-reserved)]
   " "
   [:a {:href "/"} (tr :project/legal-entity)]
   [:span.text-muted " (c) 2019"]])

(defn main-component [current-page]
  [current-page (session/get :state)])

(defn not-found-component []
  [:div.mt-3
   ;; [link-back-component]
   [:h2.mt-3.text-center (tr :not-found/header)]
   [:p.mt-3.text-center  (tr :not-found/message)]])

(defn forbidden-component []
  [:div.mt-3
   [:h2.mt-3.text-center (tr :forbidden/header)]
   [:p.mt-3.text-center  (tr :forbidden/message)]])

;; tag-cloud

(defn interpolate [x1 x2 y1 y2 x]
  (let [k (/ (- x x1) (- x2 x1))]
    (+ y1 (* k (- y2 y1)))))

(defn tag-cloud-component
  [{:keys [on-click
           min-font-size
           max-font-size]
    :or {min-font-size 1,
         max-font-size 2}}
   tags]
  (let [counts (vals tags)
        s-min (apply min counts)
        s-max (apply max counts)

        interpolate' (partial interpolate s-min s-max min-font-size max-font-size)

        tag-component
        (fn [tag count]
          [:a.mb-1.pr-4
           {:href "#"
            :on-click (partial on-click tag)
            :style {:font-size (str (float (interpolate' count)) "em")}}
           tag])]

    [:div.tag-cloud
     (->> tags
          (into [])
          (shuffle)
          (map (fn [[tag count]] (-> [tag-component tag count] (with-meta {:key tag}))))
          (intercalate [:span " "]))]))
