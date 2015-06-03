(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [time-to-read "0.1.0"]])

(ns io.perun.ttr
  {:boot/export-tasks true}
  (:require [boot.core         :as boot]
            [boot.util         :as u]
            [io.perun.core     :as perun]
            [clojure.java.io   :as io]
            [time-to-read.core :as time-to-read]))

(def ^:private
  +defaults+ {:datafile "meta.edn"})

(boot/deftask ttr
  "Calculate time to read for each file"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (perun/read-files-defs fileset (:datafile options))
              updated-files
                (map
                  (fn [file-def]
                    (let [time-to-read (time-to-read/estimate-for-text (:content file-def))]
                      (assoc file-def :ttr time-to-read)))
                  files)]
          (perun/save-files-defs tmp options updated-files)
          (u/info "Added TTR to %s files\n" (count updated-files))
          (perun/commit-and-next fileset tmp next-handler))))))
