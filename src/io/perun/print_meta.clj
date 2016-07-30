(ns io.perun.print-meta
  (:require [puget.printer :as puget]))

(defn print-meta [data]
  (puget/cprint data))
