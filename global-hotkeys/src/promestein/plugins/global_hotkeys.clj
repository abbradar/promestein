(ns promestein.plugins.global-hotkeys
  (:import [javax.swing KeyStroke])
  (:import [com.tulskiy.keymaster.common Provider HotKeyListener])
  (:require [mount.core :as mount :refer [defstate]])
  (:require [clojure.tools.logging :as log])
  (:require [clojure.tools.trace :refer [trace]])

  (:require [promestein.server :as server]))

(defn register
  "Registers a shortcut on provider, which will trigger listener"
  [provider shortcut listener]
  (let [keystroke (KeyStroke/getKeyStroke shortcut)
        keystroke-listener (proxy [HotKeyListener] []
                             (onHotKey [hot-key] (listener hot-key)))]
    (.register provider keystroke keystroke-listener)))

(defn register-from-config
  "Register all listeners from a configuration file."
  [provider config]
  (doseq [[hotkey-name listener-name] config]
    (let [hotkey (name hotkey-name)]
      (log/infof "Registering hotkey %s with action %s" hotkey listener-name)
      (let [listener (resolve (symbol listener-name))
            wrapped-listener (fn [hk]
                               (log/debugf "Received hotkey event %s" hotkey)
                               (listener))]
        (register provider hotkey wrapped-listener)))))

(defstate provider
  :start (Provider/getCurrentProvider true)
  :stop (do
          (.reset provider)
          (.stop provider)))

(defstate listeners
  :start (register-from-config provider (:global-hotkeys @server/config)))
