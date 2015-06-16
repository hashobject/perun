(ns io.perun.ttr
  (:require [boot.util         :as u]
            [potpuri.core      :as potpuri]
            [time-to-read.core :as time-to-read]))


(defn calculate-ttr [files]
  (let [updated-files
          (potpuri/map-vals
            (fn [metadata]
              (assoc metadata :ttr (time-to-read/estimate-for-text (:content metadata))))
            files)]
    (u/info "Added TTR to %s files\n" (count updated-files))
    updated-files))
