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

(defn get-host-config
  "Update frontend configuration from well-known location."
  []
  (go
    (let [config-response (<! (http/get "/config.json"))]
      (if (= (:status config-response) 200)
        (let [config-body (:body config-response)
              backend-url (:backend-url config-body)]
          (common/update-config! (dissoc config-body :backend-url))
          (common/set-backend-url! backend-url))
        (js/console.warn "No /config.json found, did you forget to place it to /public-dev?")))))

(defn top-level-component
  [_data]
  (if @common/ref-backend-url
    [ui/main-component current-page-component]
    (do
      (get-host-config)
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
