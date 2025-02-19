(ns promestein.frontend.ui.core
  (:require
   [promestein.frontend.ui.i18n :as i18n]
   [promestein.frontend.ui.common :as common]
   [promestein.frontend.ui.misc :as misc]

   [promestein.frontend.ui.forms.forms :as forms]
   [promestein.frontend.ui.forms.field :as field]
   [promestein.frontend.ui.forms.image]
   [promestein.frontend.ui.forms.select]))

(def tr i18n/tr)
(def tr-extend i18n/tr-extend)

(def intercalate          common/intercalate)
(def link-back-component  common/link-back-component)
(def with-link-back       common/with-link-back)
(def spinner-component    common/spinner-component)
(def cover-component      common/cover-component)
(def spoiler-component    common/spoiler-component)
(def lockable-component   common/lockable-component)
(def header-component     common/header-component)
(def footer-component     common/footer-component)
(def main-component       common/main-component)
(def not-found-component  common/not-found-component)
(def forbidden-component  common/forbidden-component)
(def tag-cloud-component  common/tag-cloud-component)

(def add-auth-token       common/add-auth-token)

(def with-placeholder     common/with-placeholder)
(def with-async-resource  common/with-async-resource)
(def with-ajax            common/with-ajax)
(def with-image           common/with-image)
(def with-sse             common/with-sse)

(def get-current-page       misc/get-current-page)
(def set-current-page!      misc/set-current-page!)
(def current-page-component misc/current-page-component)
(def page-component         misc/page-component)
(def redir                  misc/redir)
(def redir-component        misc/redir-component)
(def debug-component        misc/debug-component)

(def field-component      field/field-component)

(def form-component       forms/form-component)
(def modal-form-component forms/modal-form-component)
(def toggle-visibility!   forms/toggle-visibility!)
(def toggle-visibility    forms/toggle-visibility)