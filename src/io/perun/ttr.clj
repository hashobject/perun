(ns io.perun.ttr
  (:require [boot.util         :as u]
            [time-to-read.core :as time-to-read]))

(defn calculate-ttr [files-metadata]
  (let [updated-metadata
        (map
          (fn [metadata]
            (let [time-to-read (time-to-read/estimate-for-text (:content metadata))]
              (assoc metadata :ttr time-to-read)))
          files-metadata)]
    (u/info "Added TTR to %s files\n" (count updated-metadata))
    updated-metadata))
