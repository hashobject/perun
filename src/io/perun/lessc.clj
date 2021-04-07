(ns io.perun.lessc
  (:require [clojure.string :as str]
            [io.perun.core :as perun]
            [boot.from.me.raynes.conch :as conch]))

(defn lessc-out
  [& args]
  (let [p (apply conch/proc "lessc" args)
        out (conch/stream-to-string p :out)
        err (conch/stream-to-string p :err)]
    (conch/destroy p)
    (when (not= (conch/exit-code p) 0)
      (perun/report-info "lessc" err))
    out))

(defn compile-less
  [{:keys [entry]} include-dirs]
  (perun/report-debug "lessc" "compiling less" (:path entry))
  (let [lessc-args (->> [(when (seq include-dirs)
                           (str "--include-path=" include-dirs))
                         (:full-path entry)]
                        (keep identity))]
    (assoc entry :rendered (apply lessc-out lessc-args))))

(defn lessc-deps
  [{:keys [path full-path]} include-dirs]
  (perun/report-debug "lessc" "getting deps" path)
  (let [lessc-args (->> [(when (seq include-dirs)
                           (str "--include-path=" include-dirs))
                         "-M"
                         full-path
                         "perun-nowrite.css"]
                        (keep identity))
        deps (-> (apply lessc-out lessc-args)
                 (str/replace #"^perun-nowrite\.css: " "")
                 str/trim
                 (str/split #" (?=/)"))]
    (into #{} (remove empty? deps))))
