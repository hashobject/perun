(ns io.perun.print-meta
  (:require [clojure.java.io :as io]
            [io.perun.core :as perun]
            [puget.printer :as puget]))

(defn assoc-content-fn
  [content-exts]
  (fn [entry]
    (if (content-exts (str "." (perun/extension (:filename entry))))
      (assoc entry :content (-> entry :full-path io/file slurp))
      entry)))

(defn print-meta [data content-exts]
  (puget/cprint (map (assoc-content-fn content-exts) data)))
