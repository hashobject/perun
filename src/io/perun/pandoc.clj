(ns io.perun.pandoc
  (:require [io.perun.core :as perun]
            [boot.from.me.raynes.conch :as conch]))

(defn process-pandoc [{:keys [entry]} cmd-opts]
  (perun/report-debug "pandoc" "processing pandoc" (:path entry))
  (let [p (apply conch/proc "pandoc" (conj cmd-opts (:full-path entry)))
        html (conch/stream-to-string p :out)]
    (conch/destroy p)
    (when (not= (conch/exit-code p) 0)
      (perun/report-info "pandoc" "error in pandoc process for %s" (:path entry)))
    (assoc entry :rendered html)))
