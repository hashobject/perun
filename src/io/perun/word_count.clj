(ns io.perun.word-count
  (:require [io.perun.core     :as perun]))

(defn add-word-count [file]
  (if-let [content (:content file)]
    (assoc file :word-count (count (clojure.string/split content #"\s")))
    file))

(defn count-words [files]
  (let [updated-files (map add-word-count files)]
    (perun/report-info "word-count" "added word-count to %s files" (count updated-files))
    updated-files))
