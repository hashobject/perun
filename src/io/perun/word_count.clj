(ns io.perun.word-count
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]))

(defn add-word-count [file]
  (if-let [content (:content file)]
    (assoc file :word-count (count (clojure.string/split content #"\s")))
    file))

(defn count-words [files]
  (let [updated-files (map add-word-count files)]
    (u/info "Added word-count to %s files\n" (count updated-files))
    updated-files))
