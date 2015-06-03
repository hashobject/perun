(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.render
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]))


(def ^:private
  +defaults+ {:target "public"
              :datafile "meta.edn"})

(boot/deftask render
  "Render pages"
  [o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   r renderer RENDERER code "Page renderer"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (perun/read-files-defs fileset (:datafile options))]
           (doall
            (map
              (fn [file]
                (let [render-fn (:renderer options)
                      html (render-fn file)
                      page-filepath (str (:target options) "/"
                                          (or (:filepath file)
                                              (str (:filename file) ".html")))]
                  (perun/create-file tmp page-filepath html)))
            files))
          (u/info (str "Render all pages\n"))
          (perun/commit-and-next fileset tmp next-handler))))))

