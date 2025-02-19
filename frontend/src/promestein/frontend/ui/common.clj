(ns promestein.frontend.ui.common)

(defmacro defcomponent [n & body]
  (let [component-name (-> n name keyword)]
    `(let [component# (fn ~@body)]
       (defmethod promestein.frontend.ui.misc/page-component ~component-name [{:keys [~'params]}] [component# ~'params]))))
