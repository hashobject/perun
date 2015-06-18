(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [io.perun.core :as perun]))

(def ^:private global-deps
  '[[clj-time "0.9.0"]])

(defn- create-pod [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into global-deps)
      (update-in [:dependencies] into deps)
      pod/make-pod
      future))

(def ^:private markdown-deps
  '[[endophile "0.1.2"]
    [circleci/clj-yaml "0.5.3"]])

(def ^:private +markdown-defaults+
  {:create-filename "io.perun.markdown/generate-filename"})

(defn- commit [fileset tmp]
  (-> fileset
      (boot/add-resource tmp)
      boot/commit!))

(deftask markdown
  "Parse markdown files"
  [f create-filename CREATE_FILENAME str "Function that creates final target filename of the file"]
  (let [pod (create-pod markdown-deps)
        options (merge +markdown-defaults+ *opts*)
        last-markdown-files (atom nil)]
    (boot/with-pre-wrap fileset
      (let [markdown-files (->> fileset
                                (boot/fileset-diff @last-markdown-files)
                                boot/user-files
                                (boot/by-ext ["md" "markdown"])
                                (map #(.getPath (boot/tmp-file %))))]
            (do
              (reset! last-markdown-files fileset)
              (let [parsed-metadata (pod/with-call-in @pod
                                      (io.perun.markdown/parse-markdown
                                        ~markdown-files
                                        ~options))
                    initial-metadata (or (:metadata (meta fileset)) {})
                    final-metadata (merge initial-metadata parsed-metadata)
                    fs-with-meta (with-meta fileset {:metadata final-metadata})]
          fs-with-meta))))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(deftask ttr
  "Calculate time to read for each file"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [metadata (:metadata (meta fileset))
            updated-metadata (pod/with-call-in @pod
                                (io.perun.ttr/calculate-ttr ~metadata))
            fs-with-meta (with-meta fileset {:metadata updated-metadata})]
        fs-with-meta))))

(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [files (:metadata (meta fileset))
          updated-files (perun/filter-vals #(not (true? (:draft %))) files)
          fs-with-meta (with-meta fileset {:metadata updated-files})]
      (u/info "Remove draft files. Remaining %s files\n" (count updated-files))
      fs-with-meta)))

(defn- create-filepath [file]
  (assoc file :filepath (str  "/" (:filename file) "/index.html")))

(deftask permalink
  "Make files permalinked. E.x. about.html will become about/index.html"
  []
  (boot/with-pre-wrap fileset
    (let [files         (:metadata (meta fileset))
          updated-files (perun/map-vals create-filepath files)
          fs-with-meta  (with-meta fileset {:metadata updated-files})]
      (u/info "Added permalinks to %s files\n" (count updated-files))
      fs-with-meta)))

(def ^:private sitemap-deps
  '[[sitemap "0.2.4"]])

(def ^:private +sitemap-defaults+
  {:filename "sitemap.xml"
   :target "public"})

(deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str "Generated sitemap filename"
   o target   OUTDIR   str "The output directory"
   u url      URL      str "Base URL"]
  (let [pod (create-pod sitemap-deps)
        tmp (boot/tmp-dir!)
        options (merge +sitemap-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files-metadata (:metadata (meta fileset))]
        (pod/with-call-in @pod
          (io.perun.sitemap/generate-sitemap
            ~(.getPath tmp)
            ~files-metadata
            ~options))
        (commit fileset tmp)))))

(def ^:private rss-deps
  '[[clj-rss "0.1.9"]])

(def ^:private +rss-defaults+
  {:filename "feed.rss"
   :target "public"})

(deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str "Generated RSS feed filename"
   o target      OUTDIR      str "The output directory"
   t title       TITLE       str "RSS feed title"
   p description DESCRIPTION str "RSS feed description"
   l link        LINK        str "RSS feed link"]
  (let [pod (create-pod rss-deps)
        tmp (boot/tmp-dir!)
        options (merge +rss-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files-metadata (:metadata (meta fileset))]
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss
            ~(.getPath tmp)
            ~files-metadata
            ~options))
        (commit fileset tmp)))))

(def ^:private +render-defaults+
  {:out-dir "public"})

(deftask render
  "Render pages"
  [o out-dir  OUTDIR   str  "The output directory"
   r renderer RENDERER code "Page renderer"]
  (let [tmp (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (:metadata (meta fileset)))]
        (doseq [file files]
          (let [render-fn (:renderer options)
                html (render-fn file)
                page-filepath (str (:out-dir options) "/"
                                   (or (:filepath file)
                                       (str (:filename file) ".html")))]
            (perun/create-file tmp page-filepath html)))
        (u/info "Render all pages\n")
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer identity
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i1 i2))})

(deftask collection
  "Render collection files"
  [o out-dir    OUTDIR     str  "The output directory"
   r renderer   RENDERER   code "Page renderer"
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [tmp (boot/tmp-dir!)
        options (merge +collection-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (:metadata (meta fileset)))
            filtered-files (filter (:filterer options) files)
            sorted-files (sort-by (:sortby options) (:comparator options) filtered-files)
            render-fn (:renderer options)
            html (render-fn sorted-files)
            page-filepath (str (:out-dir options) "/" page)]
        (perun/create-file tmp page-filepath html)
        (u/info (str "Render collection " page "\n"))
        (commit fileset tmp)))))
