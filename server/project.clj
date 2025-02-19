(defproject promestein/server "0.1.0-SNAPSHOT"
  :description "Japanese games on-the-fly translation"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :monolith/inherit true
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [ch.qos.logback/logback-classic "1.2.10"]
                 [com.andree-surya/moji4j "1.2.0"]
                 [com.atilika.kuromoji/kuromoji-unidic-kanaaccent "0.9.0"]
                 [com.google.cloud/google-cloud-translate "2.1.10"]
                 [com.google.cloud/google-cloud-vision "2.0.19"]
                 [org.clojure/tools.logging "1.2.4"]
                 [cheshire "5.10.2"]
                 [mount "0.1.16"]
                 [hikari-cp "2.13.0"]
                 [honeysql "1.0.461"]
                 [digest "1.4.10"]
                 [org.xerial/sqlite-jdbc "3.36.0.3"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.tobereplaced/mapply "1.0.0"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-json "0.5.1"]
                 [compojure "1.6.2"] ; possibly move to reitit
                 [clj-stacktrace "0.2.8"]
                 [http-kit "2.5.3"]
                 [org.clojure/core.async "1.5.648"]

                 [org.clojure/test.check "1.1.1"] ; test dependencies
                 [p6spy/p6spy "3.9.1"]
                 [org.clojure/tools.trace "0.7.11"]

                 [promestein/utils "0.1.0-SNAPSHOT"]]

  :plugins [[lein-monolith "1.10.1"]]
  :main ^:skip-aot promestein.main
  :profiles {:uberjar {:aot :all}})
