(defproject promestein/convert-dict "0.1.0-SNAPSHOT"
  :description "Generate SQLite representation of JMDict for promestein"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :monolith/inherit true
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.match "1.1.0"]
                 [org.clojure/data.codec "0.2.0"]
                 [org.clojure/data.xml "0-UE-DEVELOPMENT"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [xerces/xercesImpl "2.12.2"]
                 [cheshire "5.13.0"]
                 [mount "0.1.21"]

                 [promestein/utils "0.1.0-SNAPSHOT"]]
  :plugins [[lein-monolith "1.10.1"]]
  :main ^:skip-aot promestein.convert-dict
  :jvm-opts ["-Xmx8g"]
  :profiles {:uberjar {:aot :all}})
