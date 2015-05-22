(ns io.perun.utils
  (:require [clojure.java.io  :as io]
            [boot.core        :as boot]))


(defn read-file [file]
  (slurp (str (:dir file) "/" (:path file))))

(defn write-to-file [out-file content]
  (doto out-file
    io/make-parents
    (spit content)))



(defn read-posts [filename]
  (let [edn-file (->> fileset boot/input-files (boot/by-name [filename]) first)
        file-content (read-file edn-file)
        posts (read-string file-content)]
    posts))