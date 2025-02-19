(defproject promestein/screenshot-x11 "0.1.0-SNAPSHOT"
  :description "Simple screenshot support via X11"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :monolith/inherit true
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.github.jnr/jnr-ffi "2.2.17"]]

  :plugins [[lein-monolith "1.10.1"]]
  :java-source-paths ["java"]
  :repl-options {:init-ns promestein.plugins.screenshot-x11}
  :profiles {:dev {:dependencies [[promestein/server "0.1.0-SNAPSHOT"]]}})
