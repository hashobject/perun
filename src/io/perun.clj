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

(deftask dump-meta
  "Utility task to dump perun metadata via boot.util/info"
  [m map-fn    MAPFN  code "function to map over metadata items before printing"]
  (boot/with-pre-wrap fileset
    (let [map-fn (or map-fn identity)]
      (prn (pr-str (map map-fn (perun/get-meta fileset)))))
    fileset))

(defn add-filedata [f]
  {:filename  (.getName (boot/tmp-file f))
   :path      (boot/tmp-path f)
   :full-path (.getPath (boot/tmp-file f))})

(deftask base
  "Adds some basic information to the perun metadata and
   establishes metadata structure."
  []
  (boot/with-pre-wrap fileset
    (let [updated-files (map add-filedata
                             (boot/user-files fileset))]
      (perun/set-meta fileset updated-files))))

(def ^:private images-meta-deps
  '[[image-resizer "0.1.8"]])

(deftask images-meta
  "Adds metadata information about images:
   - common colors
   - width
   - height
   - filetype
   Task uses ImageMagick that should be installed on the system."
  []
  (boot/with-pre-wrap fileset
    (let [pod (create-pod images-meta-deps)
          files (->> fileset
                     boot/user-files
                     (boot/by-ext ["png" "jpeg" "jpg"])
                     (map add-filedata))
          updated-files (pod/with-call-in @pod
                         (io.perun.contrib.images-meta/images-meta ~files {}))]
      (perun/set-meta fileset updated-files))))

(def ^:private markdown-deps
  '[[org.pegdown/pegdown "1.6.0"]
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
      (let [md-files (->> fileset
                          (boot/fileset-diff @prev-fs)
                          boot/user-files
                          (boot/by-ext ["md" "markdown"])
                          (map add-filedata))
            ; process all removed markdown files
            removed? (->> fileset
                          (boot/fileset-removed @prev-fs)
                          boot/user-files
                          (boot/by-ext ["md" "markdown"])
                          (map #(:path (boot/tmp-file %)))
                          set)
            updated-files (pod/with-call-in @pod
                             (io.perun.markdown/parse-markdown ~md-files ~options))
            initial-metadata (perun/merge-meta* (perun/get-meta fileset) @prev-meta)
            final-metadata   (perun/merge-meta* initial-metadata updated-files)
            final-metadata   (remove #(-> % :path removed?) final-metadata)
            fs-with-meta     (perun/set-meta fileset final-metadata)]
        (reset! prev-fs fileset)
        (reset! prev-meta final-metadata)
        fs-with-meta))))

(deftask global-metadata
  "Read global metadata from `perun.base.edn` or configured file.

   The global metadata will be attached to fileset where it can be
   read and manipulated by the tasks. Render tasks will pass this
   as the first argument to render functions."
  [n filename NAME str "filename to read global metadata from"]
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
      (let [files         (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.ttr/calculate-ttr ~files))
            fs-with-meta  (perun/set-meta fileset updated-files)]
        (u/dbug "Generated time-to-read:\n%s\n"
                (pr-str (map :ttr updated-files)))
        fs-with-meta))))

(deftask word-count
  "Count words in each file"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files         (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.word-count/count-words ~files))
            fs-with-meta  (perun/set-meta fileset updated-files)]
        (u/dbug "Counted words:\n%s\n"
                (pr-str (map :word-count updated-files)))
        fs-with-meta))))



(def ^:private gravatar-deps
  '[[gravatar "0.1.0"]])

(deftask gravatar
  "Find gravatar urls using emails"
  [s source-key SOURCE-PROP kw "email property used to lookup gravatar url"
   t target-key TARGET-PROP kw "property name to store gravatar url"]
  (let [pod (create-pod gravatar-deps)]
    (boot/with-pre-wrap fileset
      (let [files         (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.gravatar/find-gravatar ~files ~source-key ~target-key))
            fs-with-meta  (perun/set-meta fileset updated-files)]
        (u/dbug "Find gravatars:\n%s\n"
                (pr-str (map target-key updated-files)))
      fs-with-meta))))

;; Should be handled by more generic filterer options to other tasks
(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [files         (perun/get-meta fileset)
          updated-files (remove #(true? (:draft %)) files)
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
          updated-files   (map #(assoc % :build-date now) files)
          new-global-meta (assoc global-meta :build-date now)
          updated-fs      (perun/set-meta fileset updated-files)
          fs-with-meta    (perun/set-global-meta updated-fs new-global-meta)]
        (u/dbug "Added :build-date:\n%s\n"
                (pr-str (map :build-date updated-files)))
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
  [s slug-fn SLUGFN code "function to build slug from filename"]
  (boot/with-pre-wrap fileset
    (let [slug-fn       (or slug-fn default-slug-fn)
          files         (perun/get-meta fileset)
          updated-files (map #(assoc % :slug (-> % :filename slug-fn)) files)]
      (u/dbug "Generated slugs:\n%s\n"
              (pr-str (map :slug updated-files)))
      (u/info "Added slugs to %s files\n" (count updated-files))
      (perun/set-meta fileset updated-files))))


(def ^:private +permalink-defaults+
  {:permalink-fn (fn [m] (perun/absolutize-url (str (:slug m) "/index.html")))
   :filterer     identity})

(deftask permalink
  "Adds :permalink key to files metadata. Value of key will determine target path.

   Make files permalinked. E.x. about.html will become about/index.html"
  [p permalink-fn PERMALINKFN code "function to build permalink from TmpFile metadata"
   f filterer     FILTER   code "filter function"]

  (boot/with-pre-wrap fileset
    (let [options       (merge +permalink-defaults+ *opts*)
          files         (filter (:filterer options) (perun/get-meta fileset))
          assoc-perma   #(assoc % :permalink ((:permalink-fn options) %))
          updated-files (map assoc-perma files)]
      (u/dbug "Generated permalinks:\n%s\n"
              (pr-str (map :permalink updated-files)))
      (u/info "Added permalinks to %s files\n" (count updated-files))
      (perun/merge-meta fileset updated-files))))

(deftask canonical-url
  "Adds :canonical-url key to files metadata.

   The url is concatenation of :base-url in global metadata and files' permaurl.
   The base-url must end with '/'."
  []
  (boot/with-pre-wrap fileset
    (let [files    (perun/get-meta fileset)
          base-url (:base-url (perun/get-global-meta fileset))
          updated  (map #(assoc % :canonical-url (str base-url (:permalink %))))]
      (perun/set-meta fileset updated))))

(def ^:private sitemap-deps
  '[[sitemap "0.2.4"]])

(def ^:private +sitemap-defaults+
  {:filename "sitemap.xml"
   :target "public"})

(deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str "generated sitemap filename"
   o target   OUTDIR   str "the output directory"
   u url      URL      str "base URL"]
  (let [pod     (create-pod sitemap-deps)
        tmp     (boot/tmp-dir!)
        options (merge +sitemap-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (perun/get-meta fileset)]
        (pod/with-call-in @pod
          (io.perun.sitemap/generate-sitemap ~(.getPath tmp) ~files ~options))
        (commit fileset tmp)))))

(def ^:private rss-deps
  '[[clj-rss "0.2.3"]])

(def ^:private +rss-defaults+
  {:filename "feed.rss"
   :target "public"})

(deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str "generated RSS feed filename"
   o target      OUTDIR      str "the output directory"
   t title       TITLE       str "feed title"
   p description DESCRIPTION str "feed description"
   l link        LINK        str "feed link"]
  (let [pod     (create-pod rss-deps)
        tmp     (boot/tmp-dir!)
        options (merge +rss-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (perun/get-meta fileset)]
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss ~(.getPath tmp) ~files ~options))
        (commit fileset tmp)))))

(def ^:private atom-deps
  '[[org.clojure/data.xml "0.0.8"]
    [clj-time "0.9.0"]])

(def ^:private +atom-defaults+
  {:filename "atom.xml"
   :target "public"})

(deftask atom-feed
  "Generate Atom feed"
  [f filename    FILENAME    str "generated Atom feed filename"
   o target      OUTDIR      str "the output directory"
   t title       TITLE       str "feed title"
   s subtitle    SUBTITLE    str "feed subtitle"
   p description DESCRIPTION str "feed description"
   l link        LINK        str "feed link"]
  (let [pod     (create-pod atom-deps)
        tmp     (boot/tmp-dir!)
        options (merge +atom-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (perun/get-meta fileset)]
        (pod/with-call-in @pod
          (io.perun.atom/generate-atom ~(.getPath tmp) ~files ~options))
        (commit fileset tmp)))))

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

(defn- render-in-pod [pod sym global-meta file-meta]
  {:pre [(symbol? sym) (namespace sym)]}
  ;; Ensure passed seqs are vectors, otherwise lists/array-seqs etc
  ;; wrapped in parentheses will be interpreted as function calls
  (let [m (if (sequential? file-meta) (vec file-meta) file-meta)]
    (pod/with-eval-in pod
      (require '~(symbol (namespace sym)))
      ((resolve '~sym) ~global-meta ~m))))

(def ^:private +render-defaults+
  {:out-dir  "public"
   :filterer identity})

(deftask render
  "Render pages.

   If permalink is set for the file, it is used as the filepath.
   If permalink ends in slash, index.html is used as filename.
   If permalink is not set, the original filename is used with file extension set to html."
  [o out-dir  OUTDIR   str  "the output directory"
   f filterer FILTER   code "filter function"
   r renderer RENDERER sym  "page renderer (fully qualified symbol which resolves to a function)"]
  (let [pods    (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp     (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [pod   (pods fileset)
            files (filter (:filterer options) (perun/get-meta fileset))
            content-files (filter :content files)]
        (u/info "Render pages %s\n" (count content-files))
        (doseq [{:keys [path] :as file} content-files]
          (u/dbug " - %s" path)
          (let [html          (render-in-pod pod renderer (perun/get-global-meta fileset) file)
                page-filepath (perun/create-filepath
                                (:out-dir options)
                                ; If permalink ends in slash, append index.html as filename
                                (or (some-> (:permalink file)
                                            (string/replace #"/$" "/index.html")
                                            perun/url-to-path)
                                    (string/replace path #"(?i).[a-z]+$" ".html")))]
            (u/dbug " -> %s\n" page-filepath)
            (perun/create-file tmp page-filepath html)))
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer identity
   :groupby (fn [data] "index.html")
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask collection
  "Render collection files"
  [o out-dir    OUTDIR     str  "the output directory"
   r renderer   RENDERER   sym  "page renderer (fully qualified symbol resolving to a function)"
   f filterer   FILTER     code "filter function"
   s sortby     SORTBY     code "sort by function"
   g groupby    GROUPBY    code "group posts by function, keys will be used as filenames where posts (values) will be rendered"
   c comparator COMPARATOR code "sort by comparator function"
   p page       PAGE       str  "collection result page path"]
  (let [pods      (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp       (boot/tmp-dir!)
        options   (merge +collection-defaults+ *opts* (if-let [p (:page *opts*)]
                                                        {:groupby (fn [_] p)}))]
    (cond (not (fn? (:comparator options)))
              (u/fail "collection task :comparator option should implement IFn\n")
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
                    files          (perun/get-meta fileset)
                    content-files  (filter :content files)
                    filtered-files (filter (:filterer options) content-files)
                    grouped-files  (group-by (:groupby options) filtered-files)]
                (doseq [[page page-files] grouped-files]
                  (u/info "Render collection %s\n" page)
                  (let [sorted        (sort-by (:sortby options) (:comparator options) page-files)
                        html          (render-in-pod pod renderer (perun/get-global-meta fileset) sorted)
                        page-filepath (perun/create-filepath (:out-dir options) page)]
                    (perun/create-file tmp page-filepath html)))
                (commit fileset tmp))))))
