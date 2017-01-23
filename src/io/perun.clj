(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [clojure.data :as data]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [io.perun.core :as perun]
            [io.perun.meta :as pm]))

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

(defn tmp-by-ext
  "Returns boot tmpfiles from `fileset` that end with `extensions`.
  If `extensions` is empty, returns all files."
  [fileset extensions]
  (cond->> (boot/ls fileset)
    (> (count extensions) 0) (boot/by-ext extensions)))

(defn meta-by-ext
  "Returns perun metadata from `fileset`, filtered by `extensions`.
  If `extensions` is empty, returns metadata for all files."
  [fileset extensions]
  (map (partial pm/meta-from-file fileset) (tmp-by-ext fileset extensions)))

(defn filter-tmp-by-ext
  "Returns boot tmpfiles from `fileset`. `options` selects files
  that end with values in the `:extensions` key, filtered by the
  `:filterer` predicate. If `:extensions` is empty, returns all files."
  [fileset {:keys [filterer extensions]}]
  (filter (comp filterer (partial pm/meta-from-file fileset))
          (tmp-by-ext fileset extensions)))

(defn filter-meta-by-ext
  "Returns perun metadata from `fileset`. `options` selects files
  that end with values in the `:extensions` key, filtered by the
  `:filterer` predicate. If `:extensions` is empty, returns
  metadata for all files."
  [fileset {:keys [filterer extensions]}]
  (filter filterer (meta-by-ext fileset extensions)))

(def ^:private print-meta-deps
  '[[mvxcvi/puget "1.0.0"]])

(def print-meta-pod (delay (create-pod' print-meta-deps)))

(def ^:private +print-meta-defaults+
  {:map-fn identity
   :filterer identity
   :extensions []})

(deftask print-meta
  "Utility task to print perun metadata"
  [m map-fn     MAPFN      code  "function to map over metadata items before printing (default: `identity`)"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include (default: `[]`, aka, all extensions)"]
  (boot/with-pass-thru fileset
    (let [options (merge +print-meta-defaults+ *opts*)
          entries (doall (map (:map-fn options) (filter-meta-by-ext fileset options)))]
      (pod/with-call-in @print-meta-pod
        (io.perun.print-meta/print-meta ~entries)))))

(defn trace
  "Helper function, conj `kw` onto the `:io.perun/trace` metadata
  key of each entry in `entries`"
  [kw entries]
  (map #(update-in % [:io.perun/trace] (fnil conj []) kw) entries))

(deftask base
  "Deprecated - metadata based on a files' path is now automatically set when other tasks
  access metadata"
  []
  (boot/with-pass-thru _
    (u/warn (str "The `base` task is deprecated. Metadata based on a files' path is now "
                 "automatically set when other tasks access metadata\n"))))

(def ^:private mime-type-deps
  '[[com.novemberain/pantomime "2.8.0"]])

(def ^:private +mime-type-defaults+
  {:filterer identity
   :extensions []})

(deftask mime-type
  "Adds `:mime-type` and `:file-type` keys to each file's metadata"
  [_ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include (default: `[]`, aka, all extensions)"]
  (let [pod (create-pod mime-type-deps)
        options (merge +mime-type-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [metas (trace :io.perun/mime-type (filter-meta-by-ext fileset options))
            updated-metas (pod/with-call-in @pod (io.perun.mime-type/mime-type ~metas))]
        (perun/report-info "mime-type" "set `:mime-type` and `:file-type` on %s files" (count updated-metas))
        (pm/set-meta fileset updated-metas)))))

(def ^:private images-dimensions-deps
  '[[image-resizer "0.1.8"]])

(deftask images-dimensions
  "Add images' dimensions to the file metadata:
   - width
   - height"
  []
  (boot/with-pre-wrap fileset
    (let [pod (create-pod images-dimensions-deps)
          metas (trace :io.perun/images-dimensions
                       (meta-by-ext fileset [".png" ".jpeg" ".jpg"]))
          updated-metas (pod/with-call-in @pod
                         (io.perun.contrib.images-dimensions/images-dimensions ~metas {}))]
      (pm/set-meta fileset updated-metas))))

(def ^:private images-resize-deps
  '[[image-resizer "0.1.8"]])

(def ^:private +images-resize-defaults+
  {:out-dir "public"
   :resolutions #{3840 2560 1920 1280 1024 640}})

(deftask images-resize
  "Resize images to the provided resolutions.
   Each image file would have resolution appended to it's name:
   e.x. san-francisco.jpg would become san-francisco_3840.jpg"
  [o out-dir     OUTDIR       str    "the output directory"
   r resolutions RESOLUTIONS  #{int} "resoulitions to which images should be resized"]
  (boot/with-pre-wrap fileset
    (let [options (merge +images-resize-defaults+ *opts*)
          tmp (boot/tmp-dir!)
          pod (create-pod images-resize-deps)
          metas (trace :io.perun/images-resize
                       (meta-by-ext fileset [".png" ".jpeg" ".jpg"]))
          updated-metas (pod/with-call-in @pod
                         (io.perun.contrib.images-resize/images-resize ~(.getPath tmp) ~metas ~options))]
      (perun/report-debug "images-resize" "new resized images" updated-metas)
      (-> fileset
          (commit tmp)
          (pm/set-meta updated-metas)))))

;; These are modified diff functions from boot. They can be removed if/when
;; this PR is merged: https://github.com/boot-clj/boot/pull/566
(defn- diff-tree
  [tree props]
  (let [->map #(select-keys % props)]
    (reduce-kv #(assoc %1 %2 (->map %3)) {} tree)))

(defn- diff*
  [{t1 :tree :as before} {t2 :tree :as after} props]
  (if-not before
    {:added   after
     :removed (assoc after :tree {})
     :changed (assoc after :tree {})}
    (let [props        (or (seq props) [:id])
          d1           (diff-tree t1 props)
          d2           (diff-tree t2 props)
          [x y z]      (map (comp set keys) (data/diff d1 d2))
          changed-keys (set/union (set/intersection x y)
                                  (set/intersection (set/union x y) z))]
      {:added   (->> (set/difference y x changed-keys) (select-keys t2) (assoc after :tree))
       :removed (->> (set/difference x y changed-keys) (select-keys t1) (assoc after :tree))
       :changed (->> changed-keys                      (select-keys t2) (assoc after :tree))})))

(defn- fileset-diff
  [before after & props]
  (let [{:keys [added changed]}
        (diff* before after props)]
    (update-in added [:tree] merge (:tree changed))))

;;; end modified boot functions

(defn diff-filesets
  [before after uses-meta]
  (if uses-meta
    ;; Change `fileset-diff` to `boot/fileset-diff` when
    ;; https://github.com/boot-clj/boot/pull/566 is merged
    (let [diff (fileset-diff before after :hash pm/+meta-key+)]
      {:content-diff-fs diff
       :meta-diff-fs diff})
    {:content-diff-fs (fileset-diff before after :hash)
     :meta-diff-fs (fileset-diff before after pm/+meta-key+)}))

(defn content-pre-wrap
  "Wrapper for input parsing tasks. Calls `parse-form` on new or changed
  files with extensions in `extensions`, adds `tracer` to `:io.perun/trace`
  and writes files for subsequent tasks to process, if desired.
  `output-extension` can be used to indicate the type of file the task produces,
  or pass `nil` to overwrite with a file of the same name and extension. Pass
  `pod` if one is needed for parsing. Set `uses-meta` to `true` if parsing
  depends on Perun metadata"
  [{:keys [parse-form-fn extensions output-extension tracer options pod uses-meta]}]
  (let [tmp  (boot/tmp-dir!)
        prev (atom {})]
    (boot/with-pre-wrap fileset
      (let [{:keys [content-diff-fs meta-diff-fs]} (diff-filesets (:fs @prev) fileset uses-meta)
            parse-form (parse-form-fn (meta-by-ext content-diff-fs extensions))
            changed-meta (->> (if pod
                                (pod/with-call-in @pod ~parse-form)
                                (eval parse-form))
                              (pm/merge-meta (meta-by-ext meta-diff-fs extensions))
                              (trace tracer))
            input-fs (-> fileset
                         (pm/set-meta (:meta @prev))
                         (pm/set-meta changed-meta))
            input-meta (meta-by-ext input-fs extensions)
            global-meta (pm/get-global-meta fileset)
            output-meta (doall
                         (for [{:keys [path parsed filename] :as entry*} input-meta]
                           (let [out-dir (:out-dir options)
                                 ext-pattern (re-pattern (str "(" (string/join "|" extensions) ")$"))
                                 new-path (if output-extension
                                            (->> output-extension
                                                 (string/replace path ext-pattern)
                                                 (perun/create-filepath out-dir))
                                            (perun/create-filepath out-dir path))
                                 entry (-> entry*
                                           (dissoc :parsed :original)
                                           (merge {:original-path path}
                                                  (when out-dir
                                                    {:out-dir out-dir})
                                                  (pm/path-meta new-path global-meta)))]
                             (perun/create-file tmp new-path parsed)
                             entry)))
            new-fs (-> input-fs
                       (commit tmp)
                       (pm/set-meta output-meta))]
        (reset! prev {:fs fileset :meta input-meta})
        new-fs))))

(def ^:private markdown-deps
  '[[org.pegdown/pegdown "1.6.0"]
    [circleci/clj-yaml "0.5.5"]])

(def ^:private +markdown-defaults+
  {:out-dir "public"
   :meta {:original true
          :include-rss true
          :include-atom true}})

(deftask markdown
  "Parse markdown files

   This task will look for files ending with `md` or `markdown`
   and add a `:parsed` key to their metadata containing the
   HTML resulting from processing markdown file's content. Also
   writes an HTML file that contains the same content as `:parsed`"
  [d out-dir  OUTDIR  str "the output directory"
   m meta     META    edn "metadata to set on each entry; keys here will be overridden by metadata in each file"
   o options  OPTS    edn "options to be passed to the markdown parser"]
  (let [pod     (create-pod markdown-deps)
        options (merge +markdown-defaults+ *opts*)]
    (content-pre-wrap
     {:parse-form-fn (fn [meta] `(io.perun.markdown/parse-markdown ~meta ~options))
      :extensions [".md" ".markdown"]
      :output-extension ".html"
      :tracer :io.perun/markdown
      :options options
      :pod pod})))

(deftask global-metadata
  "Read global metadata from `perun.base.edn` or configured file.

   The global metadata will be attached to fileset where it can be
   read and manipulated by the tasks. Render tasks will pass this
   as the first argument to render functions."
  [n filename NAME str "filename to read global metadata from"]
  (boot/with-pre-wrap fileset
    (let [meta-file (or filename "perun.base.edn")
          global-meta (some->> fileset
                               boot/ls
                               (boot/by-name [meta-file])
                               first
                               boot/tmp-file
                               slurp
                               read-string)]
      (perun/report-info "global-metadata" "read global metadata from %s" meta-file)
      (pm/set-global-meta fileset global-meta))))

(def ^:private ttr-deps
  '[[time-to-read "0.1.0"]])

(def ^:private +ttr-defaults+
  {:filterer identity
   :extensions [".html"]})

(deftask ttr
  "Calculate time to read for each file. Add `:ttr` key to the files' meta"
  [_ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"]
  (let [pod     (create-pod ttr-deps)
        options (merge +ttr-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [meta-contents (->> (filter-tmp-by-ext fileset options)
                               (map (juxt (partial pm/meta-from-file fileset)
                                          (comp slurp boot/tmp-file))))
            updated-metas (trace :io.perun/ttr
                                 (pod/with-call-in @pod
                                   (io.perun.ttr/calculate-ttr ~meta-contents)))]
        (perun/report-debug "ttr" "generated time-to-read" (map :ttr updated-metas))
        (pm/set-meta fileset updated-metas)))))

(def ^:private +word-count-defaults+
  {:filterer identity
   :extensions [".html"]})

(deftask word-count
  "Count words in each file. Add `:word-count` key to the files' meta"
  [_ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"]
  (let [options (merge +word-count-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [updated-metas (->> (filter-tmp-by-ext fileset options)
                               (keep #(let [meta (pm/meta-from-file fileset %)
                                            file (if-let [original-path (:original-path meta)]
                                                   (boot/tmp-get fileset original-path)
                                                   %)
                                            content (-> file boot/tmp-file slurp)]
                                        (when content
                                          (assoc meta :word-count (count (string/split content #"\s"))))))
                               (trace :io.perun/word-count))]
        (perun/report-info "word-count" "added word-count to %s files" (count updated-metas))
        (perun/report-debug "word-count" "counted words" (map :word-count updated-metas))
        (pm/set-meta fileset updated-metas)))))

(def ^:private gravatar-deps
  '[[gravatar "1.1.1"]])

(def ^:private +gravatar-defaults+
  {:filterer identity
   :extensions [".html"]})

(deftask gravatar
  "Find gravatar urls using emails"
  [s source-key SOURCE-PROP kw    "email property used to lookup gravatar url"
   t target-key TARGET-PROP kw    "property name to store gravatar url"
   _ filterer   FILTER      code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS  [str] "extensions of files to include"]
  (let [pod (create-pod gravatar-deps)
        options (merge +gravatar-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [metas (filter-meta-by-ext fileset options)
            updated-metas (trace :io.perun/gravatar
                                 (pod/with-call-in @pod
                                   (io.perun.gravatar/find-gravatar ~metas ~source-key ~target-key)))]
        (perun/report-debug "gravatar" "found gravatars" (map target-key updated-metas))
        (pm/set-meta fileset updated-metas)))))

;; Should be handled by more generic filterer options to other tasks
(deftask draft
  "Exclude draft files"
  []
  (boot/with-pre-wrap fileset
    (let [meta-fn (partial pm/meta-from-file fileset)
          draft-files (filter #(-> % meta-fn :draft) (boot/ls fileset))]
      (perun/report-info "draft" "removed %s draft files" (count draft-files))
      (boot/rm fileset draft-files))))

(def ^:private +build-date-defaults+
  {:filterer identity
   :extensions [".html"]})

(deftask build-date
  "Add :date-build attribute to each file metadata and also to the global meta"
  [_ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"]
  (boot/with-pre-wrap fileset
    (let [options         (merge +build-date-defaults+ *opts*)
          global-meta     (pm/get-global-meta fileset)
          now             (java.util.Date.)
          updated-metas   (->> (filter-meta-by-ext fileset options)
                               (map #(assoc % :date-build now))
                               (trace :io.perun/build-date))
          new-global-meta (assoc global-meta :date-build now)
          updated-fs      (pm/set-meta fileset updated-metas)]
        (perun/report-debug "build-date" "added :date-build" (map :date-build updated-metas))
        (perun/report-info "build-date" "added date-build to %s files" (count updated-metas))
      (pm/set-global-meta updated-fs new-global-meta))))

(defn mv-pre-wrap
  "Abstraction for tasks that move files in the fileset"
  [{:keys [task-name path-fn tracer options]}]
  (boot/with-pre-wrap fileset
    (let [global-meta (pm/get-global-meta fileset)
          metas (filter-meta-by-ext fileset options)
          new-fs (reduce #(let [old-path (:path %2)
                                new-path (path-fn global-meta %2)]
                            (perun/report-debug task-name "Moved" [old-path new-path])
                            (-> %1
                                (boot/mv old-path new-path)
                                (pm/set-meta (trace tracer [(assoc %2 :path new-path)]))))
                         fileset
                         metas)]
      (perun/report-info task-name "Moved %s files" (count metas))
      (boot/commit! new-fs))))

(def ^:private +slug-defaults+
  {; Parses `slug` portion out of the filename in the format: YYYY-MM-DD-slug-title.ext
   ; Jekyll uses the same format by default.
   :slug-fn (fn [_ m] (->> (string/split (:filename m) #"[-\.]")
                           (drop 3)
                           drop-last
                           (string/join "-")
                           string/lower-case))
   :filterer identity
   :extensions [".html"]})

(deftask slug
  "Adds :slug key to files metadata. Slug is derived from filename."
  [s slug-fn    SLUGFN     code  "function to build slug from file metadata"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"]
  (let [{:keys [slug-fn] :as options} (merge +slug-defaults+ *opts*)
        path-fn (fn [global-meta m]
                  (let [{:keys [path filename]} m
                        slug (slug-fn global-meta m)]
                    (str (perun/parent-path path filename) slug "." (perun/extension filename))))]
    (mv-pre-wrap {:task-name "slug"
                  :path-fn path-fn
                  :tracer :io.perun/slug
                  :options options})))

(def ^:private +permalink-defaults+
  {:permalink-fn (fn [global-meta m]
                   (perun/absolutize-url
                    (string/replace (str (:parent-path m) (:slug m) "/")
                                    (re-pattern (str "^" (:doc-root global-meta)))
                                    "")))
   :filterer identity
   :extensions [".html"]})

(deftask permalink
  "Adds :permalink key to files metadata. Value of key will determine target path.

   Make files permalinked. E.x. about.html will become about/index.html"
  [p permalink-fn PERMALINKFN code  "function to build permalink from TmpFile metadata"
   _ filterer     FILTER      code  "predicate to use for selecting entries (default: `identity`)"
   e extensions   EXTENSIONS  [str] "extensions of files to include"]
  (let [{:keys [permalink-fn] :as options} (merge +permalink-defaults+ *opts*)
        path-fn (fn [global-meta m]
                  (let [permalink (permalink-fn global-meta m)]
                    (str (:doc-root global-meta)
                         (string/replace permalink #"/$" "/index.html"))))]
    (mv-pre-wrap {:task-name "permalink"
                  :path-fn path-fn
                  :tracer :io.perun/permalink
                  :options options})))

(deftask canonical-url
  "Deprecated - The `:canonical-url` key will now automatically be set in the `entry` map passed
  to your render functions, based on the location of the file in the fileset"
  [_ filterer FILTER code "predicate to use for selecting entries (default: `identity`)"]
  (boot/with-pass-thru _
    (u/warn (str "The `canonical-url` task is deprecated. The `:canonical-url` key will now "
                 "automatically be set in the `entry` map passed to your render functions, "
                 "based on the location of the file in the fileset\n"))))

(def ^:private sitemap-deps
  '[[sitemap "0.2.4"]
    [clj-time "0.12.0"]])

(def ^:private +sitemap-defaults+
  {:filename "sitemap.xml"
   :filterer identity
   :extensions [".html"]
   :out-dir "public"})

(deftask sitemap
  "Generate sitemap"
  [f filename   FILENAME   str   "generated sitemap filename"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   o out-dir    OUTDIR     str   "the output directory"
   u url        URL        str   "base URL"]
  (let [pod     (create-pod sitemap-deps)
        tmp     (boot/tmp-dir!)
        options (merge +sitemap-defaults+ *opts*)]
    (boot/with-pre-wrap fileset
      (let [metas (filter-meta-by-ext fileset options)]
        (pod/with-call-in @pod
          (io.perun.sitemap/generate-sitemap ~(.getPath tmp) ~metas ~(dissoc options :filterer)))
        (commit fileset tmp)))))

(def ^:private rss-deps
  '[[clj-rss "0.2.3"]
    [clj-time "0.12.0"]])

(def ^:private +rss-defaults+
  {:filename "feed.rss"
   :filterer :include-rss
   :extensions [".html"]
   :out-dir "public"})

(deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str   "generated RSS feed filename"
   _ filterer    FILTER      code  "predicate to use for selecting entries (default: `:include-rss`)"
   e extensions  EXTENSIONS  [str] "extensions of files to include in the feed"
   o out-dir     OUTDIR      str   "the output directory"
   t site-title  TITLE       str   "feed title"
   p description DESCRIPTION str   "feed description"
   l base-url    LINK        str   "feed link"]
  (let [pod (create-pod rss-deps)
        tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [global-meta   (pm/get-global-meta fileset)
            options       (merge +rss-defaults+ global-meta *opts*)
            metas         (filter-meta-by-ext fileset options)]
        (perun/assert-base-url (:base-url options))
        (pod/with-call-in @pod
          (io.perun.rss/generate-rss ~(.getPath tmp) ~metas ~(dissoc options :filterer)))
        (commit fileset tmp)))))

(def ^:private atom-deps
  '[[org.clojure/data.xml "0.0.8"]
    [clj-time "0.12.0"]])

(def ^:private +atom-defaults+
  {:filename "atom.xml"
   :filterer :include-atom
   :extensions [".html"]
   :out-dir "public"})

(deftask atom-feed
  "Generate Atom feed"
  [f filename    FILENAME    str   "generated Atom feed filename"
   _ filterer    FILTER      code  "predicate to use for selecting entries (default: `:include-atom`)"
   e extensions  EXTENSIONS  [str] "extensions of files to include in the feed"
   o out-dir     OUTDIR      str   "the output directory"
   t site-title  TITLE       str   "feed title"
   s subtitle    SUBTITLE    str   "feed subtitle"
   p description DESCRIPTION str   "feed description"
   l base-url    LINK        str   "feed link"]
  (let [pod (create-pod atom-deps)
        tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [global-meta   (pm/get-global-meta fileset)
            options       (merge +atom-defaults+ global-meta *opts*)
            meta-contents (->> (filter-tmp-by-ext fileset options)
                               (map #(let [meta-fn (partial pm/meta-from-file fileset)
                                           meta (meta-fn %)
                                           file (if-let [original-path (:original-path meta)]
                                                  (boot/tmp-get fileset original-path)
                                                  %)
                                           content (or (-> file meta-fn :parsed)
                                                       (-> file boot/tmp-file slurp))]
                                       [meta content])))]
        (perun/assert-base-url (:base-url options))
        (pod/with-call-in @pod
          (io.perun.atom/generate-atom ~(.getPath tmp) ~meta-contents ~(dissoc options :filterer)))
        (commit fileset tmp)))))

(def ^:private render-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]])

(def render-pod (delay (create-pod' render-deps)))

(defn- assert-renderer [sym]
  (assert (and (symbol? sym) (namespace sym))
          "Renderer must be a fully qualified symbol, i.e. 'my.ns/fun"))

(defn- render-in-pod [pod sym render-data]
  (assert-renderer sym)
  (pod/with-eval-in pod
    (require '~(symbol (namespace sym)))
    ((resolve '~sym) ~(pod/send! render-data))))

(defn render-to-paths
  "Renders paths in `data`, using `renderer` in `pod`, and writes
  the result to `tmp`.

  `data` should be a map with keys that are fileset paths, and
  values that are the map argument that `renderer` will be called with.
  The values must be maps, with the required key `:entry`, representing
  the page being rendered.

  All `:entry`s will be returned, with their `:path`s and `:canonical-url`s
  (if there is a valid `:base-url` in global metadata) set, and `tracer`
  added to `io.perun/trace`."
  [{:keys [task-name data renderer tmp tracer global-meta]}]
  (pod/with-call-in @render-pod
    (io.perun.render/update!))
  (doall
   (trace tracer
          (for [[path {:keys [entry] :as render-data}] data]
            (let [content (render-in-pod @render-pod renderer render-data)]
              (perun/create-file tmp path content)
              (perun/report-debug task-name "rendered page for path" path)
              (merge entry (pm/path-meta path global-meta)))))))

(defn render-pre-wrap
  "Handles common rendering task orchestration

  `render-paths-fn` takes two arguments: a fileset, and a map of task options.
  `options` is a map that must have a `:renderer` key, and any other keys
  that are required by `render-paths-fn`.

  Returns a boot `with-pre-wrap` result"
  [{:keys [task-name render-paths-fn options tracer]}]
  (let [tmp (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [render-paths (render-paths-fn fileset options)
            global-meta (pm/get-global-meta fileset)
            new-metadata (render-to-paths {:task-name task-name
                                           :data render-paths
                                           :renderer (:renderer options)
                                           :tmp tmp
                                           :tracer tracer
                                           :global-meta global-meta})
            rm-files (keep #(boot/tmp-get fileset (-> % :entry :path)) (vals render-paths))]
        (perun/report-info task-name "rendered %s pages" (count render-paths))
        (perun/report-debug task-name "removing files" rm-files)
        (-> fileset
            (boot/rm rm-files)
            (commit tmp)
            (pm/set-meta new-metadata))))))

(def ^:private +render-defaults+
  {:out-dir  "public"
   :filterer identity
   :extensions [".html"]})

(deftask render
  "Render individual pages for entries in perun data.

   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entries`, all entries
    - `:entry`, the entry to be rendered

   Entries can optionally be filtered by supplying a function
   to the `filterer` option."
  [o out-dir    OUTDIR     str   "the output directory (default: \"public\")"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol which resolves to a function)"
   m meta       META       edn   "metadata to set on each entry"]
  (let [options (merge +render-defaults+ *opts*)]
    (letfn [(render-paths [fileset options]
              (let [entries (vec (filter-meta-by-ext fileset options))
                    paths (reduce
                           (fn [result {:keys [path out-dir] :as entry}]
                             (let [content (slurp (boot/tmp-file (boot/tmp-get fileset path)))
                                   path-args (if (= out-dir (:out-dir options))
                                               [path]
                                               [(:out-dir options) path])
                                   new-path (apply perun/create-filepath path-args)
                                   new-entry (merge entry
                                                    meta
                                                    {:content content
                                                     :out-dir (:out-dir options)})]
                               (assoc result new-path {:meta    (pm/get-global-meta fileset)
                                                       :entries entries
                                                       :entry   new-entry})))
                           {}
                           entries)]
                paths))]
      (render-pre-wrap {:task-name "render"
                        :render-paths-fn render-paths
                        :options options
                        :tracer :io.perun/render}))))

(defn- grouped-paths
  "Produces path maps of the shape required by `render-to-paths`, based
  on the provided `fileset` and `options`."
  [task-name fileset {:keys [grouper sortby comparator out-dir] :as options}]
  (let [global-meta (pm/get-global-meta fileset)
        paths (grouper (filter-meta-by-ext fileset options))]
    (if (seq paths)
      (reduce
       (fn [result [path {:keys [entry entries]}]]
         (let [sorted      (->> entries
                                (sort-by sortby comparator)
                                (map #(assoc % :content (->> (:path %)
                                                             (boot/tmp-get fileset)
                                                             boot/tmp-file
                                                             slurp))))
               new-path    (perun/create-filepath out-dir path)
               new-entry   (assoc entry :out-dir out-dir)]
           (assoc result new-path {:meta    global-meta
                                   :entry   new-entry
                                   :entries (vec sorted)})))
       {}
       paths)
      (do
        (perun/report-info task-name (str task-name " found nothing to render"))
        []))))

(defn assortment-pre-wrap
  "Handles common assortment task orchestration

  `task-name` is used for log messages. `tracer` is a keyword that gets added
  to the `:io.perun/trace` metadata. `grouper` is a function that takes a seq
  of entries and returns a map of paths to render data (see docstring for
  `assortment` for more info)

  Returns a boot `with-pre-wrap` result"
  [{:keys [task-name tracer grouper options]}]
  (cond (not (fn? (:comparator options)))
        (u/fail (str task-name " task :comparator option should implement Fn\n"))
        (not (ifn? (:filterer options)))
        (u/fail (str task-name " task :filterer option value should implement IFn\n"))
        (not (ifn? (:sortby options)))
        (u/fail (str task-name " task :sortby option value should implement IFn\n"))
        (not (ifn? grouper))
        (u/fail (str task-name " task :grouper option value should implement IFn\n"))
        :else
        (let [;; Make sure task-level metadata gets added to each entry
              meta-grouper (fn [entries]
                             (->> entries
                                  grouper
                                  (map (fn [[path data]]
                                         [path (update-in data [:entry] #(merge (:meta options) %))]))
                                  (into {})))
              options (assoc options :grouper meta-grouper)]
          (render-pre-wrap {:task-name task-name
                            :render-paths-fn (partial grouped-paths task-name)
                            :options options
                            :tracer tracer}))))

(def ^:private +assortment-defaults+
  {:out-dir "public"
   :filterer identity
   :extensions [".html"]
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))
   :grouper #(-> {"index.html" {:entries %}})})

(deftask assortment
  "Render multiple collections
   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entry`, the metadata for this collection
    - `:entries`, all entries

   The `grouper` function will be called with a seq containing the
   entries to be grouped, and it should return a map with keys that
   are filenames and values that are maps with the keys:
    - `:entries`: the entries for each collection
    - `:entry`: (optional) page metadata for this collection

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   The `sortby` function can be used for ordering entries before rendering."
  [o out-dir    OUTDIR     str   "the output directory"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol resolving to a function)"
   g grouper    GROUPER    code  "group posts function, keys are filenames, values are to-be-rendered entries"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   s sortby     SORTBY     code  "sort entries by function"
   c comparator COMPARATOR code  "sort by comparator function"
   m meta       META       edn   "metadata to set on each collection entry"]
  (let [grouper (or grouper #(-> {"index.html" {:entries %}}))
        options (merge +assortment-defaults+ (dissoc *opts* :grouper))]
    (assortment-pre-wrap {:task-name "assortment"
                          :tracer :io.perun/assortment
                          :grouper grouper
                          :options options})))

(def ^:private +collection-defaults+
  {:out-dir "public"
   :filterer identity
   :extensions [".html"]
   :sortby :date-published
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask collection
  "Render single file for a collection of entries
   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entry`, the metadata for this collection
    - `:entries`, all entries

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   The `sortby` function can be used for ordering entries
   before rendering as well as rendering groups of entries to different pages."
  [o out-dir    OUTDIR     str   "the output directory"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol resolving to a function)"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   s sortby     SORTBY     code  "sort entries by function"
   c comparator COMPARATOR code  "sort by comparator function"
   p page       PAGE       str   "collection result page path"
   m meta       META       edn   "metadata to set on each collection entry"]
  (let [p (or page "index.html")]
    (assortment-pre-wrap {:task-name "collection"
                          :tracer :io.perun/collection
                          :grouper #(-> {p {:entries %}})
                          :options (merge +collection-defaults+ (dissoc *opts* :page))})))

(def +inject-scripts-defaults+
  {:extensions [".html"]})

(def ^:private +tags-defaults+
  {:out-dir "public"
   :filterer identity
   :extensions [".html"]
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask tags
  "Render multiple collections based on the `:tags` metadata key
   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entry`, the metadata for this collection
    - `:entries`, all entries

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   The `sortby` function can be used for ordering entries before rendering."
  [o out-dir    OUTDIR     str   "the output directory"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol resolving to a function)"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   s sortby     SORTBY     code  "sort entries by function"
   c comparator COMPARATOR code  "sort by comparator function"
   m meta       META       edn   "metadata to set on each collection entry"]
  (let [grouper (fn [entries]
                  (->> entries
                       (mapcat (fn [entry]
                                 (map #(-> [% entry]) (:tags entry))))
                       (reduce (fn [result [tag entry]]
                                 (let [path (str tag ".html")]
                                   (-> result
                                       (update-in [path :entries] conj entry)
                                       (assoc-in [path :group-meta :tag] tag))))
                               {})))]
    (assortment-pre-wrap {:task-name "tags"
                          :tracer :io.perun/tags
                          :grouper grouper
                          :options (merge +tags-defaults+ *opts*)})))

(deftask inject-scripts
  "Inject JavaScript scripts into html files.
   Use either filter to include only files matching or remove to
   include only files not matching regex."
   [s scripts    JAVASCRIPT #{str}   "JavaScript files to inject as <script> tags in <head>."
    f filter     RE         #{regex} "Regexes to filter HTML files"
    r remove     RE         #{regex} "Regexes to blacklist HTML files with"
    e extensions EXTENSIONS [str]    "extensions of files to include"]
   (let [pod  (create-pod [])
         prev (atom nil)
         out  (boot/tmp-dir!)
         {:keys [scripts filter remove extensions]} (merge +inject-scripts-defaults+ *opts*)
         filter (cond
                  filter #(boot/by-re filter %)
                  remove #(boot/by-re remove % true)
                  :else identity)]
     (fn [next-task]
       (fn [fileset]
         (let [files (->> (boot/fileset-diff @prev fileset :hash)
                          boot/ls
                          filter
                          (boot/by-ext extensions))
                scripts-contents (->> fileset
                                      boot/ls
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
           (perun/report-info "inject-scripts" "injected %s scripts into %s HTML files" (count scripts-contents) (count files))
           (reset! prev fileset)
           (next-task (-> fileset
                          (boot/add-resource out)
                          boot/commit!
                          (pm/set-meta (map (partial pm/meta-from-file fileset) files)))))))))
