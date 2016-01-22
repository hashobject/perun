(ns io.perun.file-type
  (:require [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [pantomime.mime  :as pm]))


(defn process-file [file]
  (perun/report-debug "file-type" "processing file %s" (:filename file))
  (let [io-file (-> file :full-path io/file)
        mime-type (pm/mime-type-of io-file)
        file-type (first (str/split mime-type #"/"))]
        (assoc file :mime-type mime-type :file-type file-type)))

(defn process-files [files]
  (let [updated-files (doall (map process-file files))]
    (perun/report-info "file-type" "processed %s files" (count files))
    updated-files))
