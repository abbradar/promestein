(ns promestein.frontend.core
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs-http.client :as http]
   [cljs.core.async  :refer [<!]]
   [accountant.core :as accountant]
   [secretary.core :as secretary]
   [reagent.dom :as rdom]
   [promestein.frontend.ui.core :as ui :refer [tr page-component current-page-component]]
   [promestein.frontend.intro]
   [promestein.frontend.read]
   [promestein.frontend.upload]
   [promestein.frontend.settings]
   [promestein.frontend.common   :as common]))

(defmethod page-component :default
  []
  [ui/not-found-component])

(defn get-config
  "Update frontend configuration from well-known location."
  []
  (go
    (let [new-config (-> (http/get "/config.json") <! :body)]
      (common/set-config! new-config))))

(defn top-level-component
  [data]
  (if-let [cfg @common/ref-config]
    [ui/main-component current-page-component]
    (do
      (get-config)
      [ui/spinner-component])))

(defn ^:dev/after-load start
  []
  (accountant/configure-navigation!
   {:nav-handler (fn [path] (secretary/dispatch! path))
    :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)

  (dorun
   (map #(rdom/render (:component %) (.querySelector js/document (:path %)))
        [{:path "body>header"
          :component
          [(fn []
             (let []

               [ui/header-component
                {:home {:href "/intro" :label (tr :project/name)}
                 :menu
                 (->>
                  [{:name :upload     :label (tr :upload/title) :href "/upload"}
                   {:name :read-index :label (tr :read/title)   :href "/read"}
                   {:name :read-watch :label (tr :watch/title)  :href "/read/watch"}]
                  (filter some?))

                 :controls
                 [{:name :settings :label (tr :settings/title) :href "/settings"}]}]))]}

         {:path "body>#app"   :component [top-level-component]}
         {:path "body>footer" :component [ui/footer-component]}])))

(defn ^:export main
  []
  (start))
