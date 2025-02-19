(ns promestein.frontend.components.image-map
  (:require
   [reagent.core     :as r]
   ["react-konva"    :as k]))

(defn area-component [{:keys [handlers config highlighted? selected? id coords]}]
  (let [on-mouse-over
        (fn [_] (some-> handlers :on-mouse-over (apply id ())))

        on-mouse-out
        (fn [_] (some-> handlers :on-mouse-out (apply ())))

        on-click
        (fn [e]
          (on-mouse-over e)
          (some-> handlers :on-click (apply id ())))]

    [:> k/Group
     {:on-mouse-over on-mouse-over
      :on-touchstart on-mouse-over
      :on-touchmove  on-mouse-over

      :on-mouse-out on-mouse-out

      :on-click    on-click
      :on-touchend on-click
      :on-tap      on-click}

     [:> k/Line
      {:closed true
       :fill (str "rgba(255,255,255," (if (or highlighted? selected?) "0.4" "0.25") ")")
       :stroke
       (cond
         highlighted? (-> config :color :image-map :highlighted)
         selected?    (-> config :color :image-map :selected)
         :else        (-> config :color :image-map :neutral))

       :points
       (->> coords
            (mapcat (fn [p] [(:x p) (:y p)])))}]

     ;; Alternative version
     ;; [:> k/Circle
     ;;  {:fill (if active? "yellow" "red")
     ;;   :radius r
     ;;   :x (- left r)
     ;;   :y (- top r)}]
     ]))
(defn component [& _]
  (let [ref-state
        (r/atom
         {:container {}
          :highlighted-area nil
          :selected-area    nil})

        ref-container
        (r/cursor ref-state [:container])

        ref-highlighted-area
        (r/cursor ref-state [:highlighted-area])

        ref-selected-area
        (r/cursor ref-state [:selected-area])

        on-resize
        (fn [& [_]]
          (swap! ref-container
                 #(assoc % :width (some-> % :elem .-clientWidth))))

        on-mouse-over-area
        (fn [id] (reset! ref-highlighted-area id))

        on-mouse-out-area
        (fn [] (reset! ref-highlighted-area nil))]

    (r/create-class
     {:display-name "image-map"

      :component-did-mount
      (fn [_]
        (-> js/window (.addEventListener "resize" on-resize)))

      :component-did-update
      (fn [this argv]
        (let [argv' (-> this .-props (js->clj :keywordize-keys true) :argv)]
          (when (not= (-> argv :map) (-> argv' :map))
            (reset! ref-selected-area nil)
            (reset! ref-highlighted-area nil))))

      :component-will-unmount
      (fn [_]
        (-> js/window (.removeEventListener "resize" on-resize)))

      :reagent-render
      (fn [{:keys [handlers config default] :as props} image]
        (if (nil? image)
          [:div.image-map]

          (let [{:keys [container highlighted-area]} @ref-state
                selected-area (-> @ref-selected-area (or (some-> default :selected-area)))

                image-width  (-> image .-naturalWidth)
                image-height (-> image .-naturalHeight)
                scaling (/ (-> container :width) image-width)]

            [:div.image-map
             (select-keys props [:style :class])
             [:div
              {:ref
               #(when %
                  (do
                    (swap! ref-container assoc :elem %)
                    (on-resize)))}
              [:> k/Stage
               {:width  (-> container :width)
                :height (* image-height scaling)
                :scale
                {:x scaling
                 :y scaling}}
               [:> k/Layer
                [:> k/Image
                 {:image  image
                  :width  image-width
                  :height image-height

                  :prevent-default false
                  :on-mouse-move on-mouse-out-area
                  :on-mouse-out  on-mouse-out-area
                  :on-touchstart on-mouse-out-area
                  :on-touchmove  on-mouse-out-area
                  :on-touchend   on-mouse-out-area}]

                (let [on-click-area
                      (fn [id]
                        (reset! ref-selected-area id)
                        (some-> handlers :on-select-area (apply id ())))]

                  (->> props :map
                       (filter #(-> % :coords empty? not))
                       (map #(->
                              [area-component
                               (merge
                                %
                                {:highlighted?  (= (:id %) highlighted-area)
                                 :selected?     (= (:id %) selected-area)
                                 :config config
                                 :handlers
                                 {:on-mouse-over on-mouse-over-area
                                  :on-mouse-out  on-mouse-out-area
                                  :on-click      on-click-area}})]
                              (with-meta {:key (:coords %)})))))]]]])))})))
