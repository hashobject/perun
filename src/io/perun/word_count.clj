(ns io.perun.word-count
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]))


(defn count-words [files]
  (let [add-wc #(assoc % :word-count (count (clojure.string/split (:content %) #"\s")))
        updated-files (map add-wc files)]
    (u/info "Added word-count to %s files\n" (count updated-files))
    updated-files))
