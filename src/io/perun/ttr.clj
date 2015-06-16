(ns io.perun.ttr
  (:require [boot.util         :as u]
            [time-to-read.core :as time-to-read]))

(defn calculate-ttr [files]
  (let [updated-files
        (map
          (fn [file]
            (let [metadata (val file)
                  time-to-read (time-to-read/estimate-for-text (:content metadata))]
              (assoc metadata :ttr time-to-read)))
          files)]
    (u/info "Added TTR to %s files\n" (count updated-files))
    updated-files))
