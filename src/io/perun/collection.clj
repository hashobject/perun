(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.collection
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]))


(def ^:private
  +defaults+ {:target "public"
              :datafile "meta.edn"
              :filterer identity
              :sortby (fn [file] (:date_published file))
              :comparator (fn [i1 i2] (compare i1 i2))})

(boot/deftask collection
  "Render collection files"
  [o target     OUTDIR     str  "The output directory"
   d datafile   DATAFILE   str  "Datafile with all parsed meta information"
   r renderer   RENDERER   code "Page renderer"
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (perun/read-files-defs fileset (:datafile options))
              filtered-files (filter (:filterer options) files)
              sorted-files (sort-by (:sortby options) (:comparator options) filtered-files)
              render-fn (:renderer options)
              html (render-fn sorted-files)
              page-filepath (str (:target options) "/" page)]
            (perun/create-file tmp page-filepath html)
          (u/info (str "Render collection " page "\n"))
          (perun/commit-and-next fileset tmp next-handler))))))

