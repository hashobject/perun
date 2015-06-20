(ns io.perun.ttr
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]
            [time-to-read.core :as time-to-read]))


(defn calculate-ttr [files ttr-key]
  (let [updated-files
          (perun/map-vals
            (fn [metadata]
              (assoc metadata ttr-key (time-to-read/estimate-for-text (:content metadata))))
            files)]
    (u/info "Added TTR to %s files\n" (count updated-files))
    updated-files))
