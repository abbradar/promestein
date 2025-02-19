(ns promestein.frontend.intro
  (:require-macros
   [secretary.core :refer [defroute]])
  (:require
   [promestein.frontend.ui.core
    :as ui
    :refer-macros [defcomponent]
    :refer [tr tr-extend set-current-page!]]))

(tr-extend
 {:en
  {:intro
   {:title       "Welcome to Promestein!"
    :description "TODO: some cheesy text"}}})

(defroute "/intro" []
  (set-current-page! :intro))

(defroute "/" []
  (set-current-page! :intro))

(defn page-preview-component [page]
  [:p ">>>" " " (-> page :type) " " (-> page :name)])

(defcomponent intro []
  [:div.intro.mt-3
   [:div.poster.mb-4
    [:div.container.px-0
     [:h2.w-100.text-center (tr :intro/title)]]]

   [:div.container.text-center
    [:p.lead (tr :intro/description)]]])
