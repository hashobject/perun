(ns io.perun.asciidoctor
  (:require [io.perun.core :as perun]
            [clojure.java.io :as io])
  (:import [org.asciidoctor Asciidoctor Asciidoctor$Factory]))

(def container
  (Asciidoctor$Factory/create))

(defn asciidoctor-to-html [file-content]
  (.convert container file-content {}))

(defn process-asciidoctor [{:keys [entry]}]
  (perun/report-debug "asciidoctor" "processing asciidoctor" (:filename entry))
  (let [file-content (-> entry :full-path io/file slurp)
        html         (asciidoctor-to-html file-content)]
    (assoc entry :rendered html)))
