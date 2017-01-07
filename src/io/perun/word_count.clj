(ns io.perun.word-count
  (:require [io.perun.core :as perun]))

(defn add-word-count [[meta content]]
  (when content
    (assoc meta :word-count (count (clojure.string/split content #"\s")))))

(defn count-words [meta-contents]
  (let [metas (map add-word-count meta-contents)]
    (perun/report-info "word-count" "added word-count to %s files" (count metas))
    metas))
