(ns io.perun.ttr
  (:require [io.perun.core     :as perun]
            [time-to-read.core :as time-to-read]))

(defn add-ttr [[meta content]]
  (when content
    (assoc meta :ttr (time-to-read/estimate-for-text content))))

(defn calculate-ttr [meta-contents]
  (let [metas (keep add-ttr meta-contents)]
    (perun/report-info "ttr" "added TTR to %s files" (count metas))
    metas))
