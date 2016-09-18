(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [io.perun.core :as perun]))

(def ^:private global-deps
  '[])

(defn- create-pod' [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into global-deps)
      (update-in [:dependencies] into deps)
      pod/make-pod))

(defn- create-pod
  [deps]
  (future (create-pod' deps)))

(defn- commit [fileset tmp]
  (-> fileset
      (boot/add-resource tmp)
      boot/commit!))

(def ^:private print-meta-deps
  '[[mvxcvi/puget "1.0.0"]])

(def print-meta-pod (delay (create-pod' print-meta-deps)))

(deftask print-meta
  "Utility task to print perun metadata"
  [m map-fn MAPFN code "function to map over metadata items before printing"]
  (boot/with-pre-wrap fileset
    (let [map-fn (or map-fn identity)]
      (pod/with-call-in @print-meta-pod
        (io.perun.print-meta/print-meta ~(vec (map map-fn (perun/get-meta fileset))))))
    fileset))

(def ^:private filedata-deps
  '[[com.novemberain/pantomime "2.8.0"]])

;; The namespace is stateless etc. so one is enough
(def filedata-pod (delay (create-pod' filedata-deps)))

(defn add-filedata [tmp-files]
  (pod/with-call-in @filedata-pod
    (io.perun.filedata/filedatas ~(vec (map (juxt boot/tmp-path #(.getPath (boot/tmp-file %))) tmp-files)))))

(deftask base
  "Add some basic information to the perun metadata and
   establish metadata structure."
  []
  (boot/with-pre-wrap fileset
    (let [updated-files (add-filedata (boot/user-files fileset))]
      (perun/set-meta fileset updated-files))))

(def ^:private images-dimensions-deps
  '[[image-resizer "0.1.8"]])

(deftask images-dimensions
  "Add images' dimensions to the file metadata:
   - width
   - height"
  []
  (boot/with-pre-wrap fileset
    (let [pod (create-pod images-dimensions-deps)
          files (->> fileset
                     boot/user-files
                     (boot/by-ext ["png" "jpeg" "jpg"])
                     add-filedata)
          updated-files (pod/with-call-in @pod
                         (io.perun.contrib.images-dimensions/images-dimensions ~files {}))]
      (perun/set-meta fileset updated-files))))

(def ^:private images-resize-deps
  '[[image-resizer "0.1.8"]])

(def ^:private +images-resize-defaults+
  {:out-dir "public"
   :resolutions #{3840 2560 1920 1280 1024 640}})

(deftask images-resize
  "Resize images to the provided resolutions.
   Each image file would have resolution appended to it's name:
   e.x. san-francisco.jpg would become san-francisco-3840.jpg"
  [o out-dir     OUTDIR       str    "the output directory"
   r resolutions RESOLUTIONS  #{int} "resoulitions to which images should be resized"]
  (boot/with-pre-wrap fileset
    (let [options (merge +images-resize-defaults+ *opts*)
          tmp (boot/tmp-dir!)
          pod (create-pod images-resize-deps)
          files (->> fileset
                     boot/user-files
                     (boot/by-ext ["png" "jpeg" "jpg"])
                     add-filedata)
          updated-files (pod/with-call-in @pod
                         (io.perun.contrib.images-resize/images-resize ~(.getPath tmp) ~files ~options))]
      (perun/report-debug "images-resize" "new resized images" updated-files)
      (perun/set-meta fileset updated-files)
      (commit fileset tmp))))

(def ^:private markdown-deps
  '[[org.pegdown/pegdown "1.6.0"]
    [circleci/clj-yaml "0.5.5"]])

(deftask markdown
  "Parse markdown files

   This task will look for files ending with `md` or `markdown`
   and add a `:content` key to their metadata containing the
   HTML resulting from processing markdown file's content"
  [o options OPTS edn "options to be passed to the markdown parser"]
  (let [pod       (create-pod markdown-deps)
        prev-meta (atom {})
        prev-fs   (atom nil)]
    (boot/with-pre-wrap fileset
      (let [md-files (->> fileset
                          (boot/fileset-diff @prev-fs)
                          boot/user-files
                          (boot/by-ext ["md" "markdown"])
                          add-filedata)
            ; process all removed markdown files
            removed? (->> fileset
                          (boot/fileset-removed @prev-fs)
                          boot/user-files
                          (boot/by-ext ["md" "markdown"])
                          (map #(boot/tmp-path %))
                          set)
            updated-files (pod/with-call-in @pod
                             (io.perun.markdown/parse-markdown ~md-files ~options))
            initial-metadata (perun/merge-meta* (perun/get-meta fileset) @prev-meta)
            ; Pure merge instead of `merge-with merge` (meta-meta).
            ; This is because updated metadata should replace previous metadata to
            ; correctly handle cases where a metadata key is removed from post metadata.
            final-metadata   (vals (merge (perun/key-meta initial-metadata) (perun/key-meta updated-files)))
            final-metadata   (remove #(-> % :path removed?) final-metadata)]
        (reset! prev-fs fileset)
        (reset! prev-meta final-metadata)
        (perun/set-meta fileset final-metadata)))))

(def ^:private asciidoctor-deps
  '[[org.asciidoctor/asciidoctorj "1.5.4"]
    [circleci/clj-yaml "0.5.5"]])

(def ^:private +asciidoctor-defaults+
  {:gempath       ""                          ; no given gempath
   :libraries     ["asciidoctor-diagram"]     ; asciidoctor-diagram incl.
   :header_footer false                       ; no full HTML doc
   :attributes    {:generator         "perun" ; context to document
                   :backend           "html5" ; for HTML5 output
                   :skip-front-matter ""      ; skip YAML frontmatter
                   :showtitle ""}})

(deftask asciidoctor
  "Parse asciidoc files

   This task will look for files ending with `adoc` (preferred),
   `ad`, `asc`, `adoc` or `asciidoc` and add a `:content` key to
   their metadata containing the HTML resulting from processing
   asciidoc file's content"
  [o options OPTS edn "options to be passed to the asciidoctor parser"]

  (let [options   (merge +asciidoctor-defaults+ *opts*)
        pod       (create-pod asciidoctor-deps)
        prev-meta (atom {})
        prev-fs   (atom nil)]
    (boot/with-pre-wrap fileset
      (let [ad-files (->> fileset
                          (boot/fileset-diff @prev-fs)
                          boot/user-files
                          (boot/by-ext ["ad" "asc" "adoc" "asciidoc"])
                          add-filedata)
            ; process all removed asciidoc files
            removed? (->> fileset
                          (boot/fileset-removed @prev-fs)
                          boot/user-files
                          (boot/by-ext ["ad" "asc" "adoc" "asciidoc"])
                          (map #(boot/tmp-path %))
                          set)
            updated-files (pod/with-call-in @pod
                             (io.perun.contrib.asciidoctor/parse-asciidoc ~ad-files ~(merge +asciidoctor-defaults+ options)))
            initial-metadata (perun/merge-meta* (perun/get-meta fileset) @prev-meta)
            ; Pure merge instead of `merge-with merge` (meta-meta).
            ; This is because updated metadata should replace previous metadata to
            ; correctly handle cases where a metadata key is removed from post metadata.
            final-metadata   (vals (merge (perun/key-meta initial-metadata) (perun/key-meta updated-files)))
            final-metadata   (remove #(-> % :path removed?) final-metadata)]
        (reset! prev-fs fileset)
        (reset! prev-meta final-metadata)
        (perun/set-meta fileset final-metadata)))))
;; TODO Support task option syntax

(deftask global-metadata
  "Read global metadata from `perun.base.edn` or configured file.

   The global metadata will be attached to fileset where it can be
   read and manipulated by the tasks. Render tasks will pass this
   as the first argument to render functions."
  [n filename NAME str "filename to read global metadata from"]
  (boot/with-pre-wrap fileset
    (let [meta-file (or filename "perun.base.edn")
          global-meta
            (some->> fileset
                     boot/user-files
                     (boot/by-name [meta-file])
                     first
                     boot/tmp-file
                     slurp
                     read-string)]
         (perun/report-info "global-metadata" "read global metadata from %s" meta-file)
         (perun/set-global-meta fileset global-meta))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(deftask ttr
  "Calculate time to read for each file. Add `:ttr` key to the files' meta"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files         (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.ttr/calculate-ttr ~files))]
        (perun/report-debug "ttr" "generated time-to-read" (map :ttr updated-files))
        (perun/set-meta fileset updated-files)))))

(deftask word-count
  "Count words in each file. Add `:word-count` key to the files' meta"
  []
  (let [pod (create-pod ttr-deps)]
    (boot/with-pre-wrap fileset
      (let [files         (perun/get-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.word-count/count-words ~files))]
        (perun/report-debug "word-count" "counted words" (map :word-count updated-files))
        (perun/set-meta fileset updated-files)))))


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
                            (io.perun.gravatar/find-gravatar ~files ~source-key ~target-key))]
        (perun/report-debug "gravatar" "found gravatars" (map target-key updated-files))
       (perun/set-meta fileset updated-files)))))

;; Should be handled by more generic filterer options to other tasks
(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [files         (perun/get-meta fileset)
          updated-files (remove #(true? (:draft %)) files)]
      (perun/report-info "draft" "removed draft files. Remaining %s files" (count updated-files))
      (perun/set-meta fileset updated-files))))

(deftask build-date
  "Add :date-build attribute to each file metadata and also to the global meta"
  []
  (boot/with-pre-wrap fileset
    (let [files           (perun/get-meta fileset)
          global-meta     (perun/get-global-meta fileset)
          now             (java.util.Date.)
          updated-files   (map #(assoc % :date-build now) files)
          new-global-meta (assoc global-meta :date-build now)
          updated-fs      (perun/set-meta fileset updated-files)]
        (perun/report-debug "build-date" "added :date-build" (map :date-build updated-files))
        (perun/report-info "build-date" "added date-build to %s files" (count updated-files))
      (perun/set-global-meta updated-fs new-global-meta))))

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
      (perun/report-debug "slug" "generated slugs" (map :slug updated-files))
      (perun/report-info "slug" "added slugs to %s files" (count updated-files))
      (perun/set-meta fileset updated-files))))


(def ^:private +permalink-defaults+
  {:permalink-fn (fn [m] (perun/absolutize-url (str (:slug m) "/index.html")))
   :filterer     identity})

(deftask permalink
  "Adds :permalink key to files metadata. Value of key will determine target path.

   Make files permalinked. E.x. about.html will become about/index.html"
  [p permalink-fn PERMALINKFN code "function to build permalink from TmpFile metadata"
   _ filterer     FILTER      code "predicate to use for selecting entries (default: `identity`)"]
  (boot/with-pre-wrap fileset
    (let [options       (merge +permalink-defaults+ *opts*)
          files         (filter (:filterer options) (perun/get-meta fileset))
          assoc-perma   #(assoc % :permalink ((:permalink-fn options) %))
          updated-files (map assoc-perma files)]
      (perun/report-debug "permalink"  "generated permalinks" (map :permalink updated-files))
      (perun/report-info "permalink" "added permalinks to %s files" (count updated-files))
      (perun/merge-meta fileset updated-files))))

(deftask canonical-url
  "Adds :canonical-url key to files metadata.

   The url is concatenation of :base-url in global metadata and files' permaurl.
   The base-url must end with '/'."
  []
  (boot/with-pre-wrap fileset
    (let [files         (perun/get-meta fileset)
          base-url      (perun/assert-base-url (:base-url (perun/get-global-meta fileset)))
          assoc-can-url #(assoc % :canonical-url (str base-url (:permalink %)))
          updated-files (map assoc-can-url files)]
        (perun/report-info "canonical-url" "added canonical urls to %s files" (count updated-files))
        (perun/merge-meta fileset updated-files))))

(def ^:private sitemap-deps
  '[[sitemap "0.2.4"]
    [clj-time "0.12.0"]])

(def ^:private +sitemap-defaults+
  {:filename "sitemap.xml"
   :filterer :content
   :out-dir "public"})

(deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str  "generated sitemap filename"
   _ filterer FILTER   code "predicate to use for selecting entries (default: `:content`)"
   o out-dir  OUTDIR   str  "the output directory"
   u url      URL      str  "base URL"]
  (let [pod     (create-pod sitemap-deps)
        tmp     (boot/tmp-dir!)
        options (merge +sitemap-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [files (filter (:filterer options) (perun/get-meta fileset))]
        (pod/with-call-in @pod
          (io.perun.sitemap/generate-sitemap ~(.getPath tmp) ~files ~(dissoc options :filterer)))
        (commit fileset tmp)))))

(def ^:private rss-deps
  '[[clj-rss "0.2.3"]
    [clj-time "0.12.0"]])

(def ^:private +rss-defaults+
  {:filename "feed.rss"
   :filterer :content
   :out-dir "public"})

(deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str  "generated RSS feed filename"
   _ filterer    FILTER      code "predicate to use for selecting entries (default: `:content`)"
   o out-dir     OUTDIR      str  "the output directory"
   t site-title  TITLE       str  "feed title"
   p description DESCRIPTION str  "feed description"
   l base-url    LINK        str  "feed link"]
  (let [pod (create-pod rss-deps)
        tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [global-meta   (perun/get-global-meta fileset)
            options       (merge +rss-defaults+ global-meta *opts*)
            files         (filter (:filterer options) (perun/get-meta fileset))]
        (perun/assert-base-url (:base-url options))
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss ~(.getPath tmp) ~files ~(dissoc options :filterer)))
        (commit fileset tmp)))))

(def ^:private atom-deps
  '[[org.clojure/data.xml "0.0.8"]
    [clj-time "0.12.0"]])

(def ^:private +atom-defaults+
  {:filename "atom.xml"
   :filterer :content
   :out-dir "public"})

(deftask atom-feed
  "Generate Atom feed"
  [f filename    FILENAME    str  "generated Atom feed filename"
   _ filterer    FILTER      code "predicate to use for selecting entries (default: `:content`)"
   o out-dir     OUTDIR      str  "the output directory"
   t site-title  TITLE       str  "feed title"
   s subtitle    SUBTITLE    str  "feed subtitle"
   p description DESCRIPTION str  "feed description"
   l base-url    LINK        str  "feed link"]
  (let [pod (create-pod atom-deps)
        tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [global-meta   (perun/get-global-meta fileset)
            options       (merge +atom-defaults+ global-meta *opts*)
            files         (filter (:filterer options) (perun/get-meta fileset))]
        (perun/assert-base-url (:base-url options))
        (pod/with-call-in @pod
          (io.perun.atom/generate-atom ~(.getPath tmp) ~files ~(dissoc options :filterer)))
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

(defn- assert-renderer [sym]
  (assert (and (symbol? sym) (namespace sym))
          "Renderer must be a fully qualified symbol, i.e. 'my.ns/fun"))

(defn- render-in-pod [pod sym render-data]
  (assert-renderer sym)
  (pod/with-eval-in pod
    (require '~(symbol (namespace sym)))
    ((resolve '~sym) ~(pod/send! render-data))))

(def ^:private +render-defaults+
  {:out-dir  "public"
   :filterer :content})

(deftask render
  "Render individual pages for entries in perun data.

   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entries`, all entries
    - `:entry`, the entry to be rendered

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   Filename is determined as follows:
   If permalink is set for the file, it is used as the filepath.
   If permalink ends in slash, index.html is used as filename.
   If permalink is not set, the original filename is used with file extension set to html."
  [o out-dir  OUTDIR   str  "the output directory (default: \"public\")"
   _ filterer FILTER   code "predicate to use for selecting entries (default: `:content`)"
   r renderer RENDERER sym  "page renderer (fully qualified symbol which resolves to a function)"]
  (let [pods    (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp     (boot/tmp-dir!)
        options (merge +render-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [pod   (pods fileset)
            files (filter (:filterer options) (perun/get-meta fileset))]
        (doseq [{:keys [path] :as file} files]
          (let [render-data   {:meta    (perun/get-global-meta fileset)
                               :entries (vec files)
                               :entry   file}
                html          (render-in-pod pod renderer render-data)
                page-filepath (perun/create-filepath
                                (:out-dir options)
                                ; If permalink ends in slash, append index.html as filename
                                (or (some-> (:permalink file)
                                            (string/replace #"/$" "/index.html")
                                            perun/url-to-path)
                                    (string/replace path #"(?i).[a-z]+$" ".html")))]
            (perun/report-debug "render" "rendered page for path" path)
            (perun/create-file tmp page-filepath html)))
        (perun/report-info "render" "rendered %s pages" (count files))
        (commit fileset tmp)))))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer :content
   :groupby (fn [data] "index.html")
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask collection
  "Render single file for a collection of entries
   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entries`, all entries

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   The `sortby` and `groupby` functions can be used for ordering entries
   before rendering as well as rendering groups of entries to different pages."
  [o out-dir    OUTDIR     str  "the output directory"
   r renderer   RENDERER   sym  "page renderer (fully qualified symbol resolving to a function)"
   _ filterer   FILTER     code "predicate to use for selecting entries (default: `:content`)"
   s sortby     SORTBY     code "sort entries by function"
   g groupby    GROUPBY    code "group posts by function, keys are filenames, values are to-be-rendered entries"
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
                    filtered-files (filter (:filterer options) files)
                    grouped-files  (group-by (:groupby options) filtered-files)
                    global-meta    (perun/get-global-meta fileset)
                    new-files      (doall
                                    (map
                                      (fn [[page page-files]]
                                        (do
                                          (let [sorted        (sort-by (:sortby options) (:comparator options) page-files)
                                                render-data   {:meta    global-meta
                                                               :entries (vec sorted)}
                                                html          (render-in-pod pod renderer render-data)
                                                page-filepath (perun/create-filepath (:out-dir options) page)
                                                new-entry     {:path page-filepath
                                                               :canonical-url (str (:base-url global-meta) page)
                                                               :content html
                                                               :date-build (:date-build global-meta)}]
                                            (perun/create-file tmp page-filepath html)
                                            (perun/report-info "collection" "rendered collection %s" page)
                                            new-entry)))
                                      grouped-files))
                    updated-files    (apply conj files new-files)
                    updated-fileset  (perun/set-meta fileset updated-files)]
                  (commit updated-fileset tmp))))))

(deftask inject-scripts
  "Inject JavaScript scripts into html files.
   Use either filter to include only files matching or remove to
   include only files not matching regex."
   [s scripts JAVASCRIPT #{str}   "JavaScript files to inject as <script> tags in <head>."
    f filter  RE         #{regex} "Regexes to filter HTML files"
    r remove  RE         #{regex} "Regexes to blacklist HTML files with"]
   (let [pod  (create-pod [])
         prev (atom nil)
         out  (boot/tmp-dir!)
         filter (cond
                  filter #(boot/by-re filter %)
                  remove #(boot/by-re remove % true)
                  :else identity)]
     (fn [next-task]
       (fn [fileset]
         (let [files (->> fileset
                          (boot/fileset-diff @prev)
                          boot/input-files
                          filter
                          (boot/by-ext [".html"]))
                scripts-contents (->> fileset
                                      boot/input-files
                                      (boot/by-path scripts)
                                      (map (comp slurp boot/tmp-file)))]
           (doseq [file files
                   :let [new-file (io/file out (boot/tmp-path file))]]
             (perun/report-debug "inject-scripts" "injecting scripts" (boot/tmp-path file))
             (io/make-parents new-file)
             (pod/with-call-in @pod
               (io.perun.contrib.inject-scripts/inject-scripts
                 ~scripts-contents
                 ~(.getPath (boot/tmp-file file))
                 ~(.getPath new-file))))
           (perun/report-info "inject-scripts" "injected %s scripts into %s HTML files" (count scripts-contents) (count files)))
         (reset! prev fileset)
         (next-task (-> fileset (boot/add-resource out) boot/commit!))))))
