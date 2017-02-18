(ns io.perun.render
  (:require [boot.from.backtick :as bt]
            [boot.pod :as pod]
            [boot.util :as util]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]))

(def tracker (atom nil))
(def refreshed (atom false))

(defn update! []
  (when-not @refreshed
    (swap! tracker (fn [tracker]
                     (util/dbug "Scan directories: %s\n" (pr-str (:directories pod/env)))
                     (dir/scan-dirs (or tracker (track/tracker)) (:directories pod/env))))

    ;; Only reload namespaces which are already loaded
    (swap! tracker (fn [tracker] (update tracker ::track/load (fn [load] (filter find-ns load)))))
    (let [load (::track/load @tracker)]
      (util/dbug "Unload: %s\n" (pr-str (::track/unload @tracker)))
      (util/dbug "Load: %s\n" (pr-str load))
      (swap! tracker reload/track-reload)
      (try
        (when (::reload/error @tracker)
          (util/fail "Error reloading: %s\n" (name (::reload/error-ns @tracker)))
          (throw (::reload/error @tracker)))
        (catch java.io.FileNotFoundException e
          (util/info "Reseting tracker due to file not found exception, all namespaces will be reloaded next time.\n")
          (reset! tracker (track/tracker))
          (throw e)))
      (reset! refreshed (pos? (count (remove #(= % 'io.perun.render) load))))))
  @refreshed)

(defn reset-refreshed! []
  (reset! refreshed false))

(defn render
  [renderer {:keys [entry] :as render-data}]
  (assoc entry :rendered (pod/eval-fn-call (bt/template (~renderer ~render-data)))))
