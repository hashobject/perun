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
              :datafile "meta.edn"
              :filterer identity})

(boot/deftask collection
  "Render collection files"
  [o target   OUTDIR   str  "The output directory"
   d datafile DATAFILE str  "Datafile with all parsed meta information"
   r renderer RENDERER sym  "Page renderer"
   f filterer FILTER   code "Filter function"
   p page     PAGE     str  "Collection result page path"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (util/read-files-defs fileset (:datafile options))
              filtered-files (filter (:filterer options) files)
              render-fn (resolve renderer)
              html (render-fn filtered-files)
              page-filepath (str (:target options) "/" page)]
            (util/create-file tmp page-filepath html)
          (u/info (str "Render collection " page "\n"))
          (util/commit-and-next fileset tmp next-handler))))))

