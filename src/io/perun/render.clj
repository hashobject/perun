(ns io.perun.render
  (:require [boot.pod :as pod]
            [boot.util :as util]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]))

(def tracker (atom nil))

(defn update! []
  (swap! tracker (fn [tracker]
                   (util/dbug "Scan directories: %s\n" (pr-str (:directories pod/env)))
                   (dir/scan-dirs (or tracker (track/tracker)) (:directories pod/env))))

  ;; Only reload namespaces which are already loaded
  (swap! tracker (fn [tracker] (update tracker ::track/load (fn [load] (filter find-ns load)))))
  (util/dbug "Unload: %s\n" (pr-str (::track/unload @tracker)))
  (util/dbug "Load: %s\n" (pr-str (::track/load @tracker)))
  (swap! tracker reload/track-reload)
  (try
    (when (::reload/error @tracker)
      (util/fail "Error reloading: %s\n" (name (::reload/error-ns @tracker)))
      (throw (::reload/error @tracker)))
    (catch java.io.FileNotFoundException e
      (util/info "Reseting tracker due to file not found exception, all namespaces will be reloaded next time.\n")
      (reset! tracker (track/tracker))
      (throw e))))
