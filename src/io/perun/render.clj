(ns io.perun.render
  (:require [boot.pod :as pod]
            [boot.util :as util]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]))

(def tracker (atom nil))

(defn update! []
  (swap! tracker (fn [tracker]
                   (util/dbug "Scan directories: %s\n" (pr-str (:directories pod/env)))
                   (dir/scan-dirs (or tracker (track/tracker)) (:directories pod/env))))

  (let [changed-ns (::track/load @tracker)]

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
        (throw e)))))
