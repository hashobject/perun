(ns io.perun.render-template
  (:require [boot.util         :as u]
            [io.perun.core     :as perun]
            [selmer.parser     :as selmer]))


(defn render [engine template data]
  (let [html (case engine
              :selmer (selmer/render-file template data)
              "")]
    (u/info "Rendered HTML using %s templates\n" (prn-str engine))
    html))
