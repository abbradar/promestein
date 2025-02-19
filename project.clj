(defproject promestein "MONOLITH"
  :description "Promestein games translation server."

  :plugins
  [[lein-monolith "1.10.1"]
   [lein-ancient "1.0.0-RC3"]
   [dev.weavejester/lein-cljfmt "0.13.0"]]

  :monolith
  {:project-dirs
   ["data.xml"
    "convert-dict"
    "embedded-frontend"
    "frontend"
    "global-hotkeys"
    "screenshot-vbox"
    "screenshot-windows"
    "screenshot-x11"
    "server"
    "utils"]
   
   :inherit
   [:plugins]})
