(ns io.perun.utils
  (:require [clojure.java.io :as io]
            [boot.core       :as boot]
            [clj-time.core   :as clj-time]
            [clj-time.coerce :as clj-time-coerce]
            [clj-time.format :as clj-time-format]))


(defn read-file [file]
  (slurp (str (:dir file) "/" (:path file))))

(defn write-to-file [out-file content]
  (doto out-file
    io/make-parents
    (spit content)))



(defn read-posts [fileset filename]
  (let [edn-file (->> fileset boot/input-files (boot/by-name [filename]) first)
        file-content (read-file edn-file)
        posts (read-string file-content)]
    posts))


(defn save-posts [tmp options updated-posts]
  (let [posts-file (io/file tmp (:datafile options))
        content (prn-str updated-posts)]
    (write-to-file posts-file content)))

;; Dates utils

(defn reformat-datestr [date-str initial-format final-format]
  (let [date (clj-time-format/parse (clj-time-format/formatter initial-format) date-str)]
        (clj-time-format/unparse (clj-time-format/formatter final-format) date)))


(defn str-to-date [string]
  (clj-time-coerce/to-date (clj-time-format/parse string)))
