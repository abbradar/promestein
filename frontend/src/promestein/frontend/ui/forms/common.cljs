(ns promestein.frontend.ui.forms.common)

(defn parse-int [s]
  (let [r (js/parseInt s)]
    (when (not (js/isNaN r)) r)))

(defn parse-float [s]
  (let [r (js/parseFloat s)]
    (when (not (js/isNaN r)) r)))
