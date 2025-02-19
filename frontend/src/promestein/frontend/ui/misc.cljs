(ns promestein.frontend.ui.misc
  (:require
   [reagent.core :as r]
   [reagent.session :as session]
   [accountant.core :as accountant]
   [promestein.frontend.ui.common :refer [spinner-component]]))

(defn get-current-page []
  (session/get :current-page))

(defn set-current-page!
  ([page]
   (set-current-page! page nil))
  ([page params]
   (session/put! :current-page {:page page :params params})))

(defmulti page-component
  "Get component for a given page."
  :page)

(defn current-page-component
  "Get component for current page."
  []
  (page-component (get-current-page)))

(defn redir [url & [replace?]]
  (if replace?
    (. accountant/history (replaceToken url))
    (accountant/navigate! url)))

(defn redir-component [url & [replace?]]
  (redir url replace?)
  [spinner-component])

(defn debug-component [tag f]
  (r/create-class
   {:display-name   (str "debug-" tag)

    :component-did-mount    (fn [& _] (prn :>>>DBG tag :did-mount))
    :component-will-unmount (fn [& _] (prn :>>>DBG tag :will-unmount))
    :component-did-update   (fn [& _] (prn :>>>DBG tag :did-update))

    :reagent-render f}))

