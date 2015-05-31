(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.collection
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]))


(def ^:private
  +defaults+ {:target "public"
              :datafile "posts.edn"})

(boot/deftask collection
  "Render collection files"
  [o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   r renderer RENDERER sym "Page renderer"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (util/read-files-defs fileset (:datafile options))]
           (doall
            (map
              (fn [file]
                (let [render-fn (resolve renderer)
                      html (render-fn file)
                      page-filepath (str (:target options) "/"
                                          (or (:filepath file)
                                              (str (:filename file) ".html")))]
                  (util/create-file tmp page-filepath html)))
            files))
          (u/info (str "Render all pages\n"))
          (util/commit-and-next fileset tmp next-handler))))))

