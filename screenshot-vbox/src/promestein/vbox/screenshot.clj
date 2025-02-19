(ns promestein.vbox.screenshot
  (:import [org.virtualbox_6_0 VBoxException VirtualBoxManager LockType Holder GuestMonitorStatus BitmapFormat])
  (:require [clojure.tools.trace :refer [trace]]))

(defn create-manager
  "Create VirtualBox manager instance."
  []
  (VirtualBoxManager/createInstance nil))

(defn destroy-manager
  "Destroy VirtualBox manager instance."
  [manager]
  (.cleanup manager))

(defn find-machine
  "Finds a VirtualBox virtual machine by name or UUID."
  [manager name-or-id]
  (let [vbox (.getVBox manager)]
    (try
      (.findMachine vbox name-or-id)
      (catch VBoxException e nil))))

(defn find-machine
  "Finds a VirtualBox virtual machine by name or UUID."
  [manager name-or-id]
  (let [vbox (.getVBox manager)]
    (try
      (.findMachine vbox name-or-id)
      (catch VBoxException e nil))))

(defn- get-machine-screenshot
  [manager session machine & {:keys [screen-id]}]
  (when-let [console (.getConsole session)]
    (let [display (.getDisplay console)
          width-ref (Holder.)
          height-ref (Holder.)
          origin-x-ref (Holder.)
          origin-y-ref (Holder.)
          depth-ref (Holder.)
          status-ref (Holder.)]
      (.getScreenResolution display screen-id width-ref height-ref depth-ref origin-x-ref origin-y-ref status-ref)
      (when (= (.value status-ref) (GuestMonitorStatus/Enabled))
        (let [width (.value width-ref)
              height (.value height-ref)]
          (.takeScreenShotToArray display screen-id width height (BitmapFormat/PNG)))))))

(defn get-active-machine-screenshot
  "Gets a screenshot of current active VM in PNG format."
  [manager]
  (let [vbox (.getVBox manager)
        session (.getSessionObject manager)]
    (->>
     (try
       (.getMachinesByGroups vbox [])
       (catch VBoxException e nil))
     (keep (fn [machine]
             (try
               (.lockMachine machine session (LockType/Shared))
               (try
                 (get-machine-screenshot manager session machine :screen-id 0)
                 (finally (.unlockMachine session)))
               (catch VBoxException e nil))))
     first)))

(defn get-screenshot
  "Gets a screenshot for a given VM screen in PNG format (default - primary)."
  [manager machine & {:keys [screen-id] :or {screen-id 0}}]
  (let [session (.getSessionObject manager)]
    (.lockMachine machine session (LockType/Shared))
    (try
      (get-machine-screenshot manager session machine :screen-id screen-id)
      (finally (.unlockMachine session)))))
