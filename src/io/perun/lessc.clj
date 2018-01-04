(ns io.perun.lessc
  (:require [io.perun.core :as perun]
            [boot.from.me.raynes.conch :as conch]))

(defn compile-less
  [{:keys [entry]} include-dirs]
  (perun/report-debug "lessc" "compiling lessc" (:path entry))
  (let [lessc-args (->> [(when (seq include-dirs)
                           (str "--include-path=" include-dirs))
                         (:full-path entry)]
                        (keep identity))
        p (apply conch/proc "lessc" lessc-args)
        css (conch/stream-to-string p :out)
        err (conch/stream-to-string p :err)]
    (conch/destroy p)
    (when (not= (conch/exit-code p) 0)
      (perun/report-info "lessc" err))
    (assoc entry :rendered css)))
