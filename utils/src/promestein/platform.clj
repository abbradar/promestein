(ns promestein.platform
  (:import [java.io IOException InputStreamReader BufferedReader File])
  (:require [clojure.string :as s]))

(defn is-windows?
  "Is current platform Windows?"
  []
  (->
   (System/getProperty "os.name")
   s/lower-case
   (s/includes? "windows")))

(defn find-program
  "Finds canonical path to a binary."
  [binary-name]
  (let [pb (ProcessBuilder. [(if (is-windows?) "where" "which") binary-name])]
    (try
      (let [proc (.start pb)]
        (when (= (.waitFor proc) 0)
          (-> proc .getInputStream slurp s/trim File. .getCanonicalFile)))
      (catch IOException e nil)
      (catch InterruptedException e nil))))

