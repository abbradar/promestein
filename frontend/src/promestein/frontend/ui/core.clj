(ns promestein.frontend.ui.core
  (:require
   [potemkin :refer [import-macro]]
   [promestein.frontend.ui.common :as common]))

(import-macro common/defcomponent)
