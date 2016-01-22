(ns io.perun.markdown
  (:require [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [pantomime.mime  :as pm]))


(defn process-file [file]
  (perun/report-debug "file-type" "processing file" (:filename file))
  (let [file (-> file :full-path io/file)
        mime-type (pm/mime-type-of file)]
        (merge md-metadata {:mime-type mime-type} file)))

(defn process-files [files]
  (let [updated-files (doall (map process-file files))]
    (perun/report-info "file-type" "read %s files" (count files))
    updated-files))
