(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [io.perun.core :as perun]))

(def ^:private global-deps
  '[[clj-time "0.9.0"]])

(def ^:private
  +defaults+ {})

(defn- create-pod [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into global-deps)
      (update-in [:dependencies] into deps)
      pod/make-pod
      future))

(defn find-data-file [fileset filename]
  (->> fileset boot/input-files (boot/by-name [filename]) first))

(def ^:private markdown-deps
  '[[markdown-clj "0.9.67"]
    [endophile "0.1.2"]])

(def ^:private +markdown-defaults+
  (merge +defaults+
         {:create-filename "io.perun.markdown/generate-filename"}))

(defn- commit [fileset tmp]
  (-> fileset
      (boot/add-resource tmp)
      boot/commit!))

(deftask markdown
  "Parse markdown files"
  [f create-filename CREATE_FILENAME str "Function that creates final target filename of the file"]
  (let [pod (create-pod markdown-deps)
        options (merge +markdown-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [markdown-files (->> fileset boot/user-files (boot/by-ext ["md" "markdown"]) (map #(.getPath (boot/tmp-file %))))
            parsed-metadata (pod/with-call-in @pod
                              (io.perun.markdown/parse-markdown
                                ~markdown-files
                                ~options))
            fs-with-meta (with-meta fileset {:metadata parsed-metadata})]
        fs-with-meta))))

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
    (let [files-metadata (:metadata (meta fileset))
          updated-metadata (remove #(true? (:draft %)) files-metadata)
          fs-with-meta (with-meta fileset {:metadata updated-metadata})]
      (u/info "Remove draft files. Remaining %s files\n" (count updated-metadata))
      fs-with-meta)))

(defn- create-filepath [file options]
  (let [file-path (str (:target options) "/" (:filename file) "/index.html")]
    (assoc file :filepath file-path)))

(deftask permalink
  "Make files permalinked"
  []
  (let [options (merge +defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files-metadata (:metadata (meta fileset))
            updated-metadata (map #(create-filepath % options) files-metadata)
            fs-with-meta (with-meta fileset {:metadata updated-metadata})]
        (u/info "Added permalinks to %s files\n" (count updated-metadata))
        fs-with-meta))))

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
  (merge +defaults+
         {:filename "feed.rss"
          :target "public"}))

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
  (merge +defaults+
         {:target "public"}))

(deftask render
  "Render pages"
  [o target   OUTDIR   str  "The output directory"
   r renderer RENDERER code "Page renderer"]
  (let [tmp (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files-metadata (:metadata (meta fileset))]
        (doseq [file files-metadata]
          (let [render-fn (:renderer options)
                html (render-fn file)
                page-filepath (str (:target options) "/"
                                   (or (:filepath file)
                                       (str (:filename file) ".html")))]
            (perun/create-file tmp page-filepath html)))
        (u/info "Render all pages\n")
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  (merge +defaults+
         {:target "public"
          :filterer identity
          :sortby (fn [file] (:date_published file))
          :comparator (fn [i1 i2] (compare i1 i2))}))

(deftask collection
  "Render collection files"
  [o target     OUTDIR     str  "The output directory"
   r renderer   RENDERER   code "Page renderer"
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [tmp (boot/tmp-dir!)
        options (merge +collection-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files-metadata (:metadata (meta fileset))
            filtered-files (filter (:filterer options) files-metadata)
            sorted-files (sort-by (:sortby options) (:comparator options) files-metadata)
            render-fn (:renderer options)
            html (render-fn sorted-files)
            page-filepath (str (:target options) "/" page)]
        (perun/create-file tmp page-filepath html)
        (u/info (str "Render collection " page "\n"))
        (commit fileset tmp)))))
