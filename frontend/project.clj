(defproject promestein/frontend "0.1.1-SNAPSHOT"
  :description "Web UI for Promestein"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :monolith/inherit true
  :dependencies
  [[thheller/shadow-cljs "2.28.20"]
   [org.clojure/core.async "1.7.701"]
   [cljs-http "0.1.48"]
   [reagent "1.3.0"]
   [reagent-utils "0.3.8"]
   [clj-commons/secretary "1.2.5-SNAPSHOT"]
   [com.andrewmcveigh/cljs-time "0.5.2"]
   [markdown-to-hiccup "0.6.2"]
   [com.taoensso/tempura "1.5.4"]
   [potemkin "0.4.7"]
   [venantius/accountant "0.2.5"]
   [funcool/promesa "11.0.678"]
   [com.cemerick/url "0.1.1"]
   [clojure-humanize "0.2.2"]
   [alandipert/storage-atom "2.0.1"]]

  :plugins
  [[lein-monolith "1.10.1"]])
