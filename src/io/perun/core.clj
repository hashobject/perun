(ns io.perun.core
  "Utilies which can be used in base JVM and pods."
  (:require [clojure.java.io :as io]))

(defn update-map [m f]
  (reduce-kv (fn [m k v]
    (assoc m k (f v))) {} m))

(defn write-to-file [out-file content]
  (doto out-file
    io/make-parents
    (spit content)))

(defn create-file [tmp filepath content]
  (let [file (io/file tmp filepath)]
    (write-to-file file content)))