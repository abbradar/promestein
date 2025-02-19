(ns promestein.frontend.settings
  (:require-macros [secretary.core :refer [defroute]])
  (:require
   [promestein.frontend.ui.core
    :as ui
    :refer-macros [defcomponent]
    :refer [tr-extend set-current-page!]]
   [promestein.frontend.components.dictionary :refer [iso-639-2]]
   [promestein.frontend.common :refer [ref-config]]))

(tr-extend
 {:en
  {:settings
   {:title [:i.fas.fa-cog]}}})

(defroute "/settings" []
  (set-current-page! :settings))

(defcomponent settings [{:keys [type] :as params}]
  [:div.container.mt-3
   [ui/link-back-component]
   [:br.mb-4]
   [ui/form-component
    {:method nil
     :action nil
     :ref-value ref-config}

    {:id    :ruby
     :label "Ruby"
     :type  :hash-map
     :align :vertical
     :fields
     {:format
      {:label "Format"
       :type :multi-select
       :collection-type []
       :choices
       [{:value :hiragana :label "Hiragana"}
        {:value :katakana :label "Katakana"}
        {:value :romaji :label "Romaji"}]}

      :size
      {:label "Size"
       :type :range
       :min 20
       :max 100
       :step 1
       :unit "%"}}}

    {:id    :chunk
     :label "Chunk"
     :type  :hash-map
     :align :vertical
     :fields
     {:strip-empty-tokens
      {:label "Strip empty tokens"
       :type  :switch}
      :show-autotranslation
      {:label "Show autotranslation"
       :type  :switch
       :labels
       {:on  "Translation will be displayed before the text"
        :off "Translation will be displayed after the text under a spoiler"}}}}

    {:id :pagination
     :label "Pagination title"
     :type :select
     :choices
     [{:value :position :label "Position"}
      {:value :name     :label "Page name"}]}

    {:id :user-languages
     :label "User languages"
     :type :multi-select
     :collection-type #{}
     :choices
     (->>
      iso-639-2
      (map #(hash-map
             :value (-> % first keyword)
             :label (-> % second :name)))
      (sort-by :label))}

    {:id :cache
     :label "Cache"
     :type  :hash-map
     :align :vertical
     :fields
     {:dictionary
      {:label "Dictionary"
       :type  :integer}

      :pages
      {:label "Pages"
       :type  :integer}

      :chunks
      {:label "Chunks"
       :type  :integer}}}

    {:id :color
     :label "Color"
     :type  :hash-map
     :fields
     {:token
      {:label "Tokens"
       :type :hash-map
       :align :horizontal
       :fields
       {:neutral     {:type :color :label "Neutral"}
        :highlighted {:type :color :label "Highlighted"}
        :related     {:type :color :label "Related"}
        :translated  {:type :color :label "Translated"}
        :ignored     {:type :color :label "Ignored"}}}

      :image-map
      {:label "Image map"
       :type :hash-map
       :align :horizontal
       :fields
       {:neutral     {:type :color :label "Neutral"}
        :highlighted {:type :color :label "Highlighted"}
        :selected    {:type :color :label "Selected"}}}

      :spoiler
      {:label "Spoiler"
       :type :hash-map
       :align :horizontal
       :fields
       {:foreground {:type :color :label "Foreground"}
        :background {:type :color :label "Background"}}}}}]])

