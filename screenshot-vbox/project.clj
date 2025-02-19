(defproject promestein/screenshot-vbox "0.1.0-SNAPSHOT"
  :description "Take screenshots from Oracle VirtualBox"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :repositories {"local" ~(str (.toURI (java.io.File. "maven_repository")))}
  :monolith/inherit true
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [vboxjxpcom "6.0.8"]]

  :plugins [[lein-monolith "1.10.1"]]
  :repl-options {:init-ns promestein.plugins.screenshot-vbox}
  :profiles {:dev {:dependencies [[promestein/server "0.1.0-SNAPSHOT"]]}})
