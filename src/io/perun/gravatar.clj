(ns io.perun.gravatar
  (:require [io.perun.core :as perun]
            [gravatar.core :as gr]))

(defn add-gravatar [file source-prop target-prop]
  (if-let [email (get file source-prop)]
    (assoc file target-prop (gr/avatar-url email))
    file))

(defn find-gravatar [files source-prop target-prop]
  (let [updated-files (map #(add-gravatar % source-prop target-prop) files)]
    (perun/report-info "gravatar" "added gravatar to %s files" (count updated-files))
    updated-files))
