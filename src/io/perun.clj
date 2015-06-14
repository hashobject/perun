(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [io.perun.core :as perun]))

(def ^:private global-deps
  '[[clj-time "0.9.0"]])

(def ^:private
  +defaults+ {:datafile "meta.edn"})

(defn- create-pod [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into global-deps)
      (update-in [:dependencies] into deps)
      pod/make-pod
      future))

(defn find-data-file [fileset filename]
  (->> fileset boot/input-files (boot/by-name [filename]) first))

(def ^:private markdown-deps
  '[[markdown-clj "0.9.40"]
    [endophile "0.1.2"]])

(def ^:private +markdown-defaults+
  (merge +defaults+
         {:create-filename "io.perun.markdown/generate-filename"}))

(defn- commit-and-next [fileset tmp next-handler]
  (-> fileset
      (boot/add-resource tmp)
      boot/commit!
      next-handler))

(deftask markdown
  "Parse markdown files"
  [d datafile        DATAFILE        str "Target datafile with all parsed meta information"
   f create-filename CREATE_FILENAME str "Function that creates final target filename of the file"]
  (let [pod (create-pod markdown-deps)
        tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +markdown-defaults+ *opts*)
              markdown-files (->> fileset boot/user-files (boot/by-ext [".md"]) (map #(.getPath (boot/tmp-file %))))]
          (pod/with-call-in @pod
            (io.perun.markdown/parse-markdown
              ~(.getPath tmp)
              ~options
              ~markdown-files))
          (commit-and-next fileset tmp next-handler))))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(deftask ttr
  "Calculate time to read for each file"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [pod (create-pod ttr-deps)
        tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))]
          (pod/with-call-in @pod
            (io.perun.ttr/calculate-ttr
              ~(.getPath tmp)
              ~(.getPath (boot/tmp-file datafile))
              ~options))
          (commit-and-next fileset tmp next-handler))))))

(deftask draft
  "Exclude draft files"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))
              files (perun/read-files-defs (.getPath (boot/tmp-file datafile)))
              updated-files-def (remove #(true? (:draft %)) files)]
          (perun/save-files-defs tmp options updated-files-def)
          (u/info "Remove draft files. Remaining %s files\n" (count updated-files-def))
          (commit-and-next fileset tmp next-handler))))))

(defn- create-filepath [file options]
  (let [file-path (str (:target options) "/" (:filename file) "/index.html")]
    (assoc file :filepath file-path)))

(deftask permalink
  "Make files permalinked"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))
              files (perun/read-files-defs (.getPath (boot/tmp-file datafile)))
              updated-files (map #(create-filepath % options) files)]
          (perun/save-files-defs tmp options updated-files)
          (u/info "Added permalinks to %s files\n" (count updated-files))
          (commit-and-next fileset tmp next-handler))))))

(def ^:private sitemap-deps
  '[[sitemap "0.2.4"]])

(def ^:private +sitemap-defaults+
  (merge +defaults+
         {:filename "sitemap.xml"
          :target "public"}))

(deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str "Generated sitemap filename"
   o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   u url      URL      str "Base URL"]
  (let [pod (create-pod sitemap-deps)
        tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +sitemap-defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))]
          (pod/with-call-in @pod
            (io.perun.sitemap/generate-sitemap
              ~(.getPath tmp)
              ~(.getPath (boot/tmp-file datafile))
              ~options))
          (commit-and-next fileset tmp next-handler))))))

(def ^:private rss-deps
  '[[clj-rss "0.1.9"]])

(def ^:private +rss-defaults+
  (merge +defaults+
         {:filename "feed.rss"
          :target "public"}))

(deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str "Generated RSS feed filename"
   o target      OUTDIR      str "The output directory"
   d datafile    DATAFILE    str "Datafile with all parsed meta information"
   t title       TITLE       str "RSS feed title"
   p description DESCRIPTION str "RSS feed description"
   l link        LINK        str "RSS feed link"]
  (let [pod (create-pod rss-deps)
        tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +rss-defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))]
          (pod/with-call-in @pod
            (io.perun.rss/generate-rss
              ~(.getPath tmp)
              ~(.getPath (boot/tmp-file datafile))
              ~options))
          (commit-and-next fileset tmp next-handler))))))

(def ^:private +render-defaults+
  (merge +defaults+
         {:target "public"}))

(deftask render
  "Render pages"
  [o target   OUTDIR   str  "The output directory"
   d datafile DATAFILE str  "Datafile with all parsed meta information"
   r renderer RENDERER code "Page renderer"]
  (let [tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +render-defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))
              files (perun/read-files-defs (.getPath (boot/tmp-file datafile)))]
          (doseq [file files]
            (let [render-fn (:renderer options)
                  html (render-fn file)
                  page-filepath (str (:target options) "/"
                                     (or (:filepath file)
                                         (str (:filename file) ".html")))]
              (perun/create-file tmp page-filepath html)))
          (u/info (str "Render all pages\n"))
          (commit-and-next fileset tmp next-handler))))))

(def ^:private +collection-defaults+
  (merge +defaults+
         {:target "public"
          :filterer identity
          :sortby (fn [file] (:date_published file))
          :comparator (fn [i1 i2] (compare i1 i2))}))

(deftask collection
  "Render collection files"
  [o target     OUTDIR     str  "The output directory"
   d datafile   DATAFILE   str  "Datafile with all parsed meta information"
   r renderer   RENDERER   code "Page renderer"
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [tmp (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +collection-defaults+ *opts*)
              datafile (find-data-file fileset (:datafile options))
              files (perun/read-files-defs (.getPath (boot/tmp-file datafile)))
              filtered-files (filter (:filterer options) files)
              sorted-files (sort-by (:sortby options) (:comparator options) filtered-files)
              render-fn (:renderer options)
              html (render-fn sorted-files)
              page-filepath (str (:target options) "/" page)]
            (perun/create-file tmp page-filepath html)
          (u/info (str "Render collection " page "\n"))
          (commit-and-next fileset tmp next-handler))))))
