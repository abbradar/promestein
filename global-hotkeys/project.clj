(defproject promestein/global-hotkeys "0.1.0-SNAPSHOT"
  :description "Global hotkeys support"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :monolith/inherit true
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.github.tulskiy/jkeymaster "1.3"]]

  :plugins [[lein-monolith "1.10.1"]]
  :repl-options {:init-ns promestein.plugins.global-hotkeys}
  :profiles {:dev {:dependencies [[promestein/server "0.1.0-SNAPSHOT"]]}})
