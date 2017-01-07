(ns io.perun.filedata
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [io.perun.core :as perun]
            [pantomime.mime :as pm]))

(defn filedata [[tmp-path full-path metadata]]
  (let [io-file   (io/file full-path)
        filename  (.getName io-file)
        mime-type (pm/mime-type-of io-file)
        file-type (first (string/split mime-type #"/"))]
    (merge
     metadata
     {; filename with extension
      :filename       filename
      ; filename without extension
      :short-filename (perun/filename filename)
      :path           tmp-path
      :mime-type      mime-type
      :file-type      file-type
      ; parent folder path
      :parent-path    (perun/parent-path tmp-path filename)
      :full-path      full-path
      :extension      (perun/extension filename)})))

(defn filedatas [tmp-files]
  (map filedata tmp-files))
