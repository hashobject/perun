(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [time-to-read "0.1.0"]])

(ns io.perun.draft
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]))

(def ^:private
  +defaults+ {:datafile "posts.edn"})

(boot/deftask draft
  "Exclude draft files"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (util/read-files-defs fileset (:datafile options))
              updated-files-def (remove #(true? (:draft %)) files)]
          (util/save-files-defs tmp options updated-files-def)
          (u/info "Remove draft files. Remaining %s files\n" (count updated-files-def))
          (util/commit-and-next fileset tmp next-handler))))))
