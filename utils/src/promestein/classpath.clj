(ns promestein.classpath
  "Fixed add-classpath."
  (:refer-clojure :exclude [add-classpath])
  (:import [clojure.lang DynamicClassLoader])
  (:require [dynapath.util :as dp])
  (:require [clojure.java.io :as io]))

;; Almost completely copied from https://github.com/lambdaisland/kaocha/blob/master/src/kaocha/classpath.clj

(defn- ensure-compiler-loader
  "Ensures the clojure.lang.Compiler/LOADER var is bound to a DynamicClassLoader,
  so that we can add to Clojure's classpath dynamically."
  []
  (when-not (bound? Compiler/LOADER)
    (.bindRoot Compiler/LOADER (DynamicClassLoader. (clojure.lang.RT/baseLoader)))))

(defn classloader-tip
  "Returns classloader used by Clojure compiler."
  []
  (ensure-compiler-loader)
  (deref clojure.lang.Compiler/LOADER))

(defn classloader-hierarchy
  "Returns a seq of classloaders, with the tip of the hierarchy first.
   Uses the current thread context ClassLoader as the tip ClassLoader
   if one is not provided."
  ([]
   (classloader-hierarchy (classloader-tip)))
  ([tip]
   (->> tip
        (iterate #(.getParent %))
        (take-while boolean))))

(defn modifiable-classloader?
  "Returns true iff the given ClassLoader is of a type that satisfies
   the dynapath.dynamic-classpath/DynamicClasspath protocol, and it can
   be modified."
  [cl]
  (dp/addable-classpath? cl))

;; When changing this test that:
;; 1. plugin-screenshot-x11 works on a hotkey from plugin-global-hotkeys;
;; 2. plugin-embedded-frontend works.
(defn add-classpath
  "A corollary to the (deprecated) `add-classpath` in clojure.core. This implementation
   requires a java.io.File or String path to a jar file or directory, and will attempt
   to add that path to the right classloader (with the search rooted at the current
   thread's context classloader)."
  ([jar-or-dir classloader]
   (if-not (dp/add-classpath-url classloader (.toURL (.toURI (io/file jar-or-dir))))
     (throw (IllegalStateException. (str classloader " is not a modifiable classloader")))))
  ([jar-or-dir]
   ;; First add it to local dynamic class loader so that `require` works in this thread.
   (let [classloaders (classloader-hierarchy)]
     (if-let [cl (->> classloaders (filter modifiable-classloader?) last)]
       (add-classpath jar-or-dir cl)
       (throw (IllegalStateException. (str "Could not find a suitable classloader to modify from "
                                           classloaders)))))
   ;; Second set thread context class loader to the tip so that `resource` works in this thread
   ;; _and_ `require` works in other threads.
   (.setContextClassLoader (Thread/currentThread) (classloader-tip))))
