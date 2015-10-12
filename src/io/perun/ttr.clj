(ns io.perun.ttr
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]
            [time-to-read.core :as time-to-read]))

(defn add-ttr [file]
      (if-let [content (:content file)]
        (assoc file :ttr (time-to-read/estimate-for-text content))
        file))

(defn calculate-ttr [files]
  (let [updated-files (map add-ttr files)]
    (u/info "Added TTR to %s files\n" (count updated-files))
    updated-files))
