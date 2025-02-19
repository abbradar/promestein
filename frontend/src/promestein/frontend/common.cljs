(ns promestein.frontend.common
  (:require
   [clojure.string :as s]
   [reagent.core :as r]
   [promestein.frontend.ui.core :as ui :refer [tr-extend]]
   [promestein.frontend.ui.cache :refer [lru-cache]]))

(tr-extend
 {:en
  {:project
   {:name         "Promestein"
    :legal-entity "Vector Ltd."}}})

(tr-extend
 {:en
  {:footer
   {:all-rights-reserved "All rights reserved"}}})

(defonce ref-config (r/atom nil))

(def default-config
  {:ruby
   {:format [:romaji :hiragana]
    :size   50}

   :chunk
   {:strip-empty-tokens   true
    :show-autotranslation false}

   :pagination :position

   :user-languages [:eng]

   :color
   {:token
    {:neutral     "#D8D8D8"
     :highlighted "#FF8888"
     :related     "#FFFF88"
     :translated  "#88EE88"
     :ignored     "#F8F8F8"}

    :image-map
    {:neutral     "#FF0000"
     :highlighted "#FFFF00"
     :selected    "#FF8800"}

    :spoiler
    {:foreground "#888888"
     :background "#EEEEEE"}}})

(defn set-config!
  [config]
  (reset! ref-config (-> (merge default-config config)
                         (update :backend-url #(s/replace % #"<hostname>" (-> js/window .-location .-hostname)))
                         (update-in [:ruby :format] #(map keyword %))
                         (update :user-languages #(->> % (map keyword) (into #{}))))))

(defn error-component [e]
  (condp = (:status e)
    404 [ui/not-found-component]
    403 [ui/forbidden-component]
    (prn :error e)))

(def default-placeholder-props
  (let [fallback-cache (lru-cache 20)]
    {:loading-component ui/spinner-component
     :error-component   error-component
     :cache             fallback-cache}))

(defn with-ajax' [component props args]
  (if-let [backend-url (:backend-url @ref-config)]
    (ui/with-ajax
      component
      (-> default-placeholder-props
          (merge props)
          (update :preprocess
                  #(comp (fn [{:keys [path params]}]
                           {:url    (str backend-url path)
                            :params (merge {:with-credentials? false} params)})
                         %)))
      args)
    [ui/spinner-component]))

(defn with-image' [component props path]
  (if-let [backend-url (:backend-url @ref-config)]
    (ui/with-image
      component
      (-> default-placeholder-props
          (merge props)
          (update :preprocess #(comp (fn [src] (str backend-url src)) %)))
      path)
    [ui/spinner-component]))