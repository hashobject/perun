(ns io.perun.mime-type
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [pantomime.mime :as pm]))

(defn mime-type [paths]
  (map (fn [{:keys [full-path] :as meta}]
         (let [io-file   (io/file full-path)
               mime-type (pm/mime-type-of io-file)
               file-type (first (string/split mime-type #"/"))]
           (merge meta
                  {:mime-type mime-type
                   :file-type file-type})))
       paths))
