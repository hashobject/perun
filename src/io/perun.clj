(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [clojure.string :as string]
            [io.perun.core :as perun]))

(def ^:private global-deps
  '[])

(defn- create-pod [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into global-deps)
      (update-in [:dependencies] into deps)
      pod/make-pod
      future))

(defn- commit [fileset tmp]
  (-> fileset
      (boot/add-resource tmp)
      boot/commit!))


(def +perun-meta-key+ :io.perun)

(defn ^:private get-perun-meta [fileset]
  (-> fileset meta +perun-meta-key+))

(defn ^:private with-perun-meta [fileset perun-data]
  (with-meta fileset (assoc (meta fileset) +perun-meta-key+ perun-data)))

(def ^:private markdown-deps
  '[[endophile "0.1.2"]
    [circleci/clj-yaml "0.5.3"]])

(deftask markdown
  "Parse markdown files

  This task will look for files ending with `md` or `markdown`
  and add a `:content` key to their metadata containing the
  HTML resulting from processing the markdown file's content"
  []
  (let [pod (create-pod markdown-deps)
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
                                      (io.perun.markdown/parse-markdown ~markdown-files))
                    files (get-perun-meta fileset)
                    initial-metadata (or files {})
                    final-metadata (merge initial-metadata parsed-metadata)
                    fs-with-meta (with-perun-meta fileset final-metadata)]
          fs-with-meta))))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(deftask ttr
  "Calculate time to read for each file"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files (get-perun-meta fileset)
            updated-files (pod/with-call-in @pod
                                (io.perun.ttr/calculate-ttr ~files))
            fs-with-meta (with-perun-meta fileset updated-files)]
       (u/dbug "Generated time-to-read:\n%s\n"
              (pr-str (map :ttr (vals updated-files))))
        fs-with-meta))))

(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [files         (get-perun-meta fileset)
          updated-files (perun/filter-vals #(not (true? (:draft %))) files)
          fs-with-meta  (with-perun-meta fileset updated-files)]
      (u/info "Remove draft files. Remaining %s files\n" (count updated-files))
      fs-with-meta)))

(defn ^:private default-slug-fn [filename]
  "Parses `slug` portion out of the filename in the format: YYYY-MM-DD-slug-title.ext

  Jekyll uses the same format by default."
  (->> (string/split filename #"[-\.]")
       (drop 3)
       drop-last
       (string/join "-")
       string/lower-case))

(deftask slug
  "Adds :slug key to files metadata. Slug is derived from filename."
  [s slug-fn SLUGFN code "Function to build slug from filename"]
  (boot/with-pre-wrap fileset
    (let [slug-fn       (or slug-fn default-slug-fn)
          files         (get-perun-meta fileset)
          updated-files (into {}
                              (for [[f m] files]
                                [f (assoc m :slug (slug-fn f))]))]
      (u/dbug "Generated Slugs:\n%s\n"
              (pr-str (map :slug (vals updated-files))))
      (u/info "Added slugs to %s files\n" (count updated-files))
      (with-perun-meta fileset updated-files))))

(defn ^:private default-permalink-fn [metadata]
  (str  "/" (:slug metadata) "/index.html"))

(deftask permalink
  "Adds :permalink key to files metadata. Value of key will determine target path.

   Make files permalinked. E.x. about.html will become about/index.html"
  [f permalink-fn PERMALINKFN code "Function to build permalink from TmpFile metadata"]
  (boot/with-pre-wrap fileset
    (let [files         (get-perun-meta fileset)
          permalink-fn  (or permalink-fn default-permalink-fn)
          assoc-perma   (fn [f] (assoc f :permalink (permalink-fn f)))
          updated-files (perun/map-vals assoc-perma files)]
      (u/dbug "Generated Permalinks:\n%s\n"
              (pr-str (map :permalink (vals updated-files))))
      (u/info "Added permalinks to %s files\n" (count updated-files))
      (with-perun-meta fileset updated-files))))

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
      (let [files (vals (get-perun-meta fileset))]
        (pod/with-call-in @pod
          (io.perun.sitemap/generate-sitemap
            ~(.getPath tmp)
            ~files
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
      (let [files (vals (get-perun-meta fileset))]
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss
            ~(.getPath tmp)
            ~files
            ~options))
        (commit fileset tmp)))))

(def ^:private +render-defaults+
  {:out-dir "public"})

(deftask render
  "Render pages"
  [o out-dir  OUTDIR   str  "The output directory"
   r renderer RENDERER code "Page renderer"]
  (let [tmp     (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (get-perun-meta fileset))]
        (doseq [file files]
          (let [render-fn (:renderer options)
                html (render-fn file)
                page-filepath (str (:out-dir options) "/"
                                   (or (:permalink file)
                                       (str (:filename file) ".html")))]
            (perun/create-file tmp page-filepath html)))
        (u/info "Render all pages\n")
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer identity
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

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
      (let [files (vals (get-perun-meta fileset))
            filtered-files (filter (:filterer options) files)
            sorted-files (sort-by (:sortby options) (:comparator options) filtered-files)
            render-fn (:renderer options)
            html (render-fn sorted-files)
            page-filepath (str (:out-dir options) "/" page)]
        (perun/create-file tmp page-filepath html)
        (u/info (str "Render collection " page "\n"))
        (commit fileset tmp)))))
