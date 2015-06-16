(ns io.perun.ttr
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]
            [time-to-read.core :as time-to-read]))


(defn calculate-ttr [files]
  (let [updated-files
          (perun/update-map files
            (fn [metadata]
              (assoc metadata :ttr (time-to-read/estimate-for-text (:content metadata)))))]
    (u/info "Added TTR to %s files\n" (count updated-files))
    updated-files))
