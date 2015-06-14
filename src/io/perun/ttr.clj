(ns io.perun.ttr
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]
            [clojure.java.io   :as io]
            [time-to-read.core :as time-to-read]))

(defn calculate-ttr [tgt-path datafile-path options]
  (let [files (perun/read-files-defs datafile-path)
        updated-files
        (map
          (fn [file-def]
            (let [time-to-read (time-to-read/estimate-for-text (:content file-def))]
              (assoc file-def :ttr time-to-read)))
          files)]
    (perun/save-files-defs tgt-path options updated-files)
    (u/info "Added TTR to %s files\n" (count updated-files))))
