(ns io.perun.gravatar
  (:require [boot.util     :as u]
            [io.perun.core :as perun]
            [gravatar      :as gr]))

(defn add-gravatar [file]
  (if-let [email (get file source-prop)]
    (assoc file target-prop (gr/avatar-url email))
    file))

(defn find-gravatar [files source-prop target-prop]
  (let [updated-files (map add-gravatar files)]
    (u/info "Added gravatar to %s files\n" (count updated-files))
    updated-files))
