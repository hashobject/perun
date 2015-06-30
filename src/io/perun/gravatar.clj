(ns io.perun.gravatar
  (:require [boot.util     :as u]
            [io.perun.core :as perun]
            [gravatar      :as gr]))


(defn find-gravatar [files source-prop target-prop]
  (let [updated-files
        (perun/map-vals
          (fn [metadata]
            (assoc metadata target-prop (gr/avatar-url (get metadata source-prop))))
          files)]
    (u/info "Added gravatar to %s files\n" (count updated-files))
    updated-files))
