(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [clojure.string :as string]
            [clojure.edn :as edn]
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

(def ^:private markdown-deps
  '[[endophile "0.1.2"]
    [circleci/clj-yaml "0.5.3"]])

(deftask markdown
  "Parse markdown files

  This task will look for files ending with `md` or `markdown`
  and add a `:content` key to their metadata containing the
  HTML resulting from processing the markdown file's content"
  [o options OPTS edn "options to be passed to endophile"]
  (let [pod       (create-pod markdown-deps)
        prev-meta (atom {})
        prev-fs   (atom nil)]
    (boot/with-pre-wrap fileset
      (let [markdown-files   (->> fileset
                                  (boot/fileset-diff @prev-fs)
                                  boot/user-files
                                  (boot/by-ext ["md" "markdown"])
                                  (map #(.getPath (boot/tmp-file %))))
            removed-files    (->> fileset
                                  (boot/fileset-removed @prev-fs)
                                  boot/user-files
                                  (boot/by-ext ["md" "markdown"])
                                  (map #(.getName (boot/tmp-file %))))
            parsed-metadata  (pod/with-call-in @pod
                               (io.perun.markdown/parse-markdown ~markdown-files
                                                                 ~options))
            initial-metadata @prev-meta
            final-metadata   (merge initial-metadata parsed-metadata)
            final-metadata   (apply dissoc final-metadata removed-files)
            fs-with-meta     (perun/set-meta fileset final-metadata)]
        (reset! prev-fs fileset)
        (reset! prev-meta final-metadata)
        fs-with-meta))))

(deftask global-metadata
  "Read global metadata from `perun.base.edn` or configured file.

   The global metadata will be attached to fileset where it can be
   read and manipulated by the tasks. Render tasks will pass this
   as the first argument to render functions."
  [n filename NAME str "filename where to read global metadata"]
  (boot/with-pre-wrap fileset
    (perun/set-global-meta
      fileset
      (some->> fileset
               boot/user-files
               (boot/by-name [(or filename "perun.base.edn")])
               first
               boot/tmp-file
               slurp
               read-string))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(deftask ttr
  "Calculate time to read for each file"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.ttr/calculate-ttr ~files))
            fs-with-meta  (perun/set-meta fileset updated-files)]
        (u/dbug "Generated time-to-read:\n%s\n"
                (pr-str (map :ttr (vals updated-files))))
        fs-with-meta))))

(def ^:private gravatar-deps
  '[[gravatar "0.1.0"]])

(deftask gravatar
  "Find gravatar urls using emails"
  [s source-key SOURCE-PROP kw "Email property used to lookup gravatar url"
   t target-key TARGET-PROP kw "Property name to store gravatar url"]
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.gravatar/find-gravatar ~files ~source-key ~target-key))
            fs-with-meta  (perun/set-meta fileset updated-files)]
        (u/dbug "Find gravatars:\n%s\n"
                (pr-str (map target-key (vals updated-files))))
      fs-with-meta))))

(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [files         (perun/get-meta fileset)
          updated-files (perun/filter-vals #(not (true? (:draft %))) files)
          fs-with-meta  (perun/set-meta fileset updated-files)]
      (u/info "Remove draft files. Remaining %s files\n" (count updated-files))
      fs-with-meta)))

(deftask build-date
  "Add :build-date attribute to each file metadata and also to the global meta"
  []
  (boot/with-pre-wrap fileset
    (let [files           (perun/get-meta fileset)
          global-meta     (perun/get-global-meta fileset)
          now             (java.util.Date.)
          updated-files   (perun/map-vals #(assoc % :build-date now) files)
          new-global-meta (assoc global-meta :build-date now)
          updated-fs      (perun/set-meta fileset updated-files)
          fs-with-meta    (perun/set-global-meta updated-fs new-global-meta)]
        (u/dbug "Added :build-date:\n%s\n"
                (pr-str (map :build-date (vals updated-files))))
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
          files         (perun/get-meta fileset)
          updated-files (into {}
                              (for [[f m] files]
                                [f (assoc m :slug (slug-fn f))]))]
      (u/dbug "Generated Slugs:\n%s\n"
              (pr-str (map :slug (vals updated-files))))
      (u/info "Added slugs to %s files\n" (count updated-files))
      (perun/set-meta fileset updated-files))))

(defn ^:private default-permalink-fn [metadata]
  (perun/absolutize-url (str (:slug metadata) "/index.html")))

(deftask permalink
  "Adds :permalink key to files metadata. Value of key will determine target path.

   Make files permalinked. E.x. about.html will become about/index.html"
  [f permalink-fn PERMALINKFN code "Function to build permalink from TmpFile metadata"]
  (boot/with-pre-wrap fileset
    (let [files         (perun/get-meta fileset)
          permalink-fn  (or permalink-fn default-permalink-fn)
          assoc-perma   (fn [f] (assoc f :permalink (permalink-fn f)))
          updated-files (perun/map-vals assoc-perma files)]
      (u/dbug "Generated Permalinks:\n%s\n"
              (pr-str (map :permalink (vals updated-files))))
      (u/info "Added permalinks to %s files\n" (count updated-files))
      (perun/set-meta fileset updated-files))))

(deftask canonical-url
  "Adds :canonical-url key to files metadata.

   The url is concatenation of :base-url in global metadata and files' permaurl.
   The base-url must end with '/'."
  []
  (boot/with-pre-wrap fileset
    (->> fileset
         perun/get-meta
         (perun/map-vals (fn [{:keys [permalink] :as post}]
                           (assoc post :canonical-url (str (:base-url (perun/get-global-meta fileset)) permalink))))
         (perun/set-meta fileset))))

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
  (let [pod     (create-pod sitemap-deps)
        tmp     (boot/tmp-dir!)
        options (merge +sitemap-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (perun/get-meta fileset))]
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
  (let [pod     (create-pod rss-deps)
        tmp     (boot/tmp-dir!)
        options (merge +rss-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (perun/get-meta fileset))]
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss
            ~(.getPath tmp)
            ~files
            ~options))
        (commit fileset tmp)))))

(def ^:private atom-deps
  '[[org.clojure/data.xml "0.0.8"]
    [clj-time "0.9.0"]])

(def ^:private +atom-defaults+
  {:filename "atom.xml"
   :target "public"})

(deftask atom-feed
  "Generate Atom feed"
  [f filename    FILENAME    str "Generated Atom feed filename"
   o target      OUTDIR      str "The output directory"
   t title       TITLE       str "Atom feed title"
   s subtitle    SUBTITLE    str "Atom feed subtitle"
   p description DESCRIPTION str "Atom feed description"
   l link        LINK        str "Atom feed link"]
  (let [pod     (create-pod atom-deps)
        tmp     (boot/tmp-dir!)
        options (merge +atom-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (vals (perun/get-meta fileset))]
        (pod/with-call-in @pod
          (io.perun.atom/generate-atom
            ~(.getPath tmp)
            ~files
            ~options))
        (commit fileset tmp)))))

(def ^:private +render-defaults+
  {:out-dir "public"})

(defn- wrap-pool [pool]
  (let [prev (atom nil)]
    (fn [fileset]
      ; Do not refresh on the first run
      (let [pod (if (and @prev
                         (seq (->> fileset
                                   (boot/fileset-diff @prev)
                                   boot/input-files
                                   (boot/by-ext ["clj" "cljc"]))))
                  (pool :refresh)
                  (pool))]
        (reset! prev fileset)
        pod))))

(defn- render-in-pod [pod sym global-meta file]
  {:pre [(symbol? sym) (namespace sym)]}
  (pod/with-eval-in pod
    (require '~(symbol (namespace sym)))
    ((resolve '~sym) ~global-meta ~file)))

(deftask render
  "Render pages.

   If permalink is set for the file, it is used as the filepath else. If permalink
   ends in slash, index.html is used as filename. If permalink is not set, the
   original filename is used with file extension set to html."
  [o out-dir  OUTDIR   str  "The output directory"
   r renderer RENDERER sym  "Page renderer. Must be fully qualified symbol which resolves to a function."]
  (let [pods    (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp     (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [pod   (pods fileset)
            files (perun/get-meta fileset)]
        (doseq [[filename file] files]
          (let [html          (render-in-pod pod renderer (perun/get-global-meta fileset) file)
                page-filepath (perun/create-filepath
                                (:out-dir options)
                                ; If permalink ends in slash, append index.html as filename
                                (or (some-> (:permalink file)
                                            (string/replace #"/$" "/index.html")
                                            perun/url-to-path)
                                    (string/replace filename #"(?i).[a-z]+$" ".html")))]
            (perun/create-file tmp page-filepath html)))
        (u/info "Render all pages\n")
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer identity
   :groupby (fn [x] "index.html")
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask collection
  "Render collection files"
  [o out-dir    OUTDIR     str  "The output directory"
   r renderer   RENDERER   sym  "Page renderer. Fully qualified symbol resolving to a function."
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   g groupby    GROUPBY    code "Group posts by function, keys will be used as filenames where posts (values) will be rendered"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [pods      (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp       (boot/tmp-dir!)
        options   (merge +collection-defaults+ *opts* (if-let [p (:page *opts*)]
                                                        {:groupby (fn [_] p)}))]
    (cond (not (fn? (:comparator options)))
              (u/fail "collection task :comparator option should be a function\n")
          (not (ifn? (:filterer options)))
              (u/fail "collection task :filterer option value should implement IFn\n")
          (and (:page options) (:groupby *opts*))
              (u/fail "using the :page option will render any :groupby option setting effectless\n")
          (not (ifn? (:groupby options)))
              (u/fail "collection task :groupby option value should implement IFn\n")
          (not (ifn? (:sortby options)))
              (u/fail "collection task :sortby option value should implement IFn\n")
          :else
            (boot/with-pre-wrap fileset
              (let [pod            (pods fileset)
                    files          (vals (perun/get-meta fileset))
                    filtered-files (filter (:filterer options) files)
                    grouped-files  (group-by (:groupby options) filtered-files)]
                (doseq [[page files] grouped-files]
                  (let [sorted-files  (vec (sort-by (:sortby options) (:comparator options) files))
                        html          (render-in-pod pod renderer (perun/get-global-meta fileset) sorted-files)
                        page-filepath (perun/create-filepath (:out-dir options) page)]
                    (perun/create-file tmp page-filepath html)
                    (u/info (str "Render collection " page "\n"))))
                (commit fileset tmp))))))
