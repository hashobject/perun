(ns io.perun.ttr
  (:require [io.perun.core     :as perun]
            [time-to-read.core :as time-to-read]))

(defn add-ttr [file]
  (if-let [content (:content file)]
    (assoc file :ttr (time-to-read/estimate-for-text content))
    file))

(defn calculate-ttr [files]
  (let [updated-files (map add-ttr files)]
    (perun/report-info "ttr" "added TTR to %s files" (count updated-files))
    updated-files))
