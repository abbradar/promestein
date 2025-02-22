(ns promestein.frontend.components.dictionary
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core     :as r]
   [clojure.string   :as str]
   [cljs-http.client :as http]
   [cljs.core.async  :as a :refer [<!]]
   ["iso-639-2"      :as iso-639-2-data]
   [promestein.frontend.ui.core :as ui]
   [promestein.frontend.ui.cache :as cache :refer [lru-cache]]
   [promestein.frontend.common :refer [ref-config ref-backend-url with-ajax']]
   [promestein.frontend.components.token :as token]))

(defonce iso-639-2
  (->> (array-seq iso-639-2-data)
       (map #(vector (.-iso6392B %)
                     {:iso-639-1 (.-iso6391 %)
                      :name      (.-name %)}))
       (into (hash-map))))

(defonce ref-entities
  (r/atom nil))

(defonce entities-reload
  (r/track! (fn []
              (when-let [backend-url @ref-backend-url]
                (go (let [response (<! (http/get (str backend-url "/dictionary/entities")
                                                 {:with-credentials? false}))]
                      (if-not (:success response)
                        (throw (ex-info "Failed to get entities" {:response response}))
                        (reset! ref-entities (response :body)))))))))

(def with-entries-data
  (let [ref-cache (cache/reactive lru-cache (r/cursor ref-config [:cache :dictionary]))]
    (fn [component token]
      (with-ajax' component
        {:cache      @ref-cache
         :preprocess
         (fn [token]
           {:path "/dictionary/query"
            :params
            {:query-params
             {:normal-form (-> token :normal-form)
              :lang        (->> @ref-config :user-languages (map name) (str/join ","))}}})}
        (-> token
            (dissoc :sense-index)
            (dissoc :dict-entry-id))))))

;; TODO: show hint
(defn dictionary-entity-title [x]
  (get @ref-entities (keyword x)))

(defn badge-component
  "Render a badge with a given text. Used for dictionary entries."
  [class-names label title]
  [:span.badge {:class class-names :title title} label])

(defn badge-entity-component [class-names label]
  [badge-component class-names label (dictionary-entity-title label)])

(defn badge-language-component [lang]
  (let [{:keys [name iso-639-1]} (iso-639-2 lang)]
    [badge-component "badge-secondary" iso-639-1 name]))

(defn format-word [word {:keys [frequency info]}]
  (->>
   [word
    (when frequency
      [:sub.frequency (* 500 frequency) "-"  (* 500 (inc frequency))])
    (when (-> info empty? not)
      (->> info
           (map #(-> [badge-component "badge-info" %] (with-meta {:key %})))))]
   (filter some?)
   (apply vector :span.word)))

(defn format-x-ele [x-ele]
  [:span.words
   (->> (into [] x-ele)
        (sort-by #(or (-> % second :frequency) 100))
        (map #(let [key (-> % first name)]
                (with-meta (format-word key (second %)) {:key key})))
        (ui/intercalate [:span ", "]))])

(defn format-gloss [{:keys [content lang type gender]}]
  [:span
   content
   (when type
     [badge-entity-component "badge-secondary" type])
   (when gender
     [badge-entity-component "badge-secondary" gender])])

(defn reference-component [{:keys [target reb sense]}]
  ^{:key (str/join "-" (filter some? [target reb sense]))}
  [:span target])

(defn sense-component [{:keys [handlers selected?]} {:keys [id gloss] :as s}]
  [:button.sense.list-group-item.list-group-item-action.noselect
   (if selected?
     {:class "list-group-item-success"
      :on-click (fn [_] (some-> handlers :on-select (apply nil ())))}
     {:on-click (fn [_] (some-> handlers :on-select (apply id ())))})
   [:p.gloss.my-0.py-0
    (->>
     [(when-let [lang (get (first gloss) :lang)]
        (vector ^{:key "lang"} [badge-language-component lang]))

      (when-let [pos (-> s :pos)]
        (for [x pos]
          ^{:key (str x "-pos")} [badge-entity-component "badge-success" x]))

      (when-let [misc (-> s :misc)]
        (for [x misc]
          ^{:key (str x "-misc")} [badge-entity-component "badge-info" x]))

      (when-let [field (s :field)]
        (for [x field]
          ^{:key (str x "-field")} [badge-entity-component "badge-warning" x]))

      (when-let [dialect (s :dialect)]
        (for [x dialect]
          ^{:key (str x "-dialect")} [badge-entity-component "badge-warning" x]))]
     (filter some?)
     (apply concat)
     (ui/intercalate [:span " "]))
    " "
    (when (not (empty? gloss))
      (->> (for [[i g] (map-indexed vector gloss)] ^{:key (str i "-gloss")} [format-gloss g])
           (ui/intercalate [:span "; "])))]

   (when-let [info (-> s :info)]
     (for [x info]
       ^{:key (str x "-info")} [:p.info.py-0.my-0 x]))

   (when-let [source (-> s :source)]
     [:p.source
      (for [{:keys [content lang part wasei]} source]
        ^{:key content}
        [:span
         (->>
          [^{:key "lang"} [badge-language-component lang]
           (when wasei
             ^{:key "wasey"} [badge-component "badge-primary" "wasei" "pseudo-loanword"])]
          (filter some?)
          (ui/intercalate [:span " "]))
         " "
         (if (not (empty? content))
           content
           "* as-is *")])])

   (when-let [antonym (s :antonym)]
     [:p.antonym
      [badge-component "badge-danger" "ant" "antonym"]
      " "
      (ui/intercalate [:span " "] (map reference-component antonym))])

   (when-let [reference (s :reference)]
     [:p.reference
      [badge-component "badge-primary" "ref" "reference"]
      " "
      (ui/intercalate [:span " "] (map reference-component reference))])])

(defn bubble-up [p xs]
  (if-let [x (->> xs (filter p) first)]
    (conj (filter (comp not p) xs) x)
    xs))

(defn entry-component [{:keys [handlers sense-id]} {:keys [id k-ele r-ele sense]}]
  (when (-> sense empty? not)
    [:div.entry.mb-3
     [:h5
      (when (not (empty? k-ele))
        [:span.card-title [format-x-ele k-ele]])
      " "
      (if (empty? k-ele)
        [:span.card-title [format-x-ele r-ele]]
        [:span.card-subtitle.ml-2.text-muted [format-x-ele r-ele]])]
     [:div.card-body.list-group.list-group-flush.py-0
      (->> (cond->> sense
             (some? sense-id) (bubble-up #(-> % :id (= sense-id))))
           (map #(-> [sense-component
                      {:selected? (some-> sense-id (= (:id %)))
                       :handlers
                       {:on-select
                        (fn [sense-id]
                          (some-> handlers :on-select (apply {:entry-id id :sense-id sense-id} ())))}} %]
                     (with-meta {:key (:id %)}))))]]))

(defn entries-list-component [props token entries]
  [:div.modal-body
   [:div
    (if (empty? entries)
      [:p.text-center.text-nuted "(No data)"]

      (let [entry-id (-> token :dict-entry-id)]
        (->> (cond->> entries
               (some? entry-id) (bubble-up #(-> % :id (= entry-id))))
             (map
              #(-> [entry-component
                    {:handlers (-> props :handlers)
                     :sense-id (when (-> % :id (= entry-id)) (-> token :sense-index))} %]
                   (with-meta {:key (:id %)}))))))]])

(defn header-component [props token]
  (let [lemma-reading (-> token :info :lemma-reading)
        normal-form   (-> token :normal-form)]
    [:div.modal-header
     [:h5.modal-title
      [:span.word normal-form]
      (let [rs (token/format-ruby (-> props :config :ruby :format) lemma-reading)]
        (when (-> rs empty? not)
          [:span.word.ml-2.text-muted "[" (str/join ", " rs) "]"]))]
     [:button.close
      {:type "button"
       :on-click (some-> props :handlers :on-close)}
      [:span "×"]]]))

(defn component [props token]
  [:div.dictionary
   [:div.modal.d-block
    {:on-click
     (fn [e]
       (when (= (-> e .-target) (-> e .-currentTarget))
         (some-> props :handlers :on-close (apply e ()))))
     :style {:overflow-y "auto"}}
    [ui/lockable-component
     [:div.modal-dialog
      [:div.modal-content
       [header-component props token]
       (-> [entries-list-component props token]
           (with-entries-data token))]]
     (-> props :locked?)]]])
