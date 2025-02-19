(ns promestein.frontend.ui.i18n
  (:require-macros
   [taoensso.tempura])
  (:require
   [taoensso.tempura :as tempura]
   [clojure.string :as str]))

(def ^:private global-dictionary {})

(defn merge-dictionaries [lhs rhs]
  (if (and (map? lhs) (map? rhs))
    (merge-with merge-dictionaries lhs rhs)
    (if (= lhs rhs)
      lhs
      (do
        (js/console.warn "Inconsistent dictionary structure" {:lhs lhs :rhs rhs})
        rhs))))

(defn tr-extend [dictionary]
  (set! global-dictionary (merge-dictionaries global-dictionary dictionary)))

(defn tr [& resource-path]
  (tempura/tr
   {:dict global-dictionary
    :resource-compiler #(constantly %)}
   [:en]
   (conj (into [] resource-path)
         (str "!!" (str/join ", " resource-path)))))
