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

(def ^:private ^:deps global-deps
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

(def ^:private ^:deps print-meta-deps
  '[[mvxcvi/puget "1.0.0"]])

(def print-meta-pod (delay (create-pod' print-meta-deps)))

(def ^:private +print-meta-defaults+
  {:map-fn identity
   :filterer identity
   :extensions []
   :content-exts #{}})

(deftask print-meta
  "Utility task to print perun metadata"
  [m map-fn       MAPFN       code   "function to map over metadata items before printing (default: `identity`)"
   _ filterer     FILTER      code   "predicate to use for selecting entries (default: `identity`)"
   e extensions   EXTENSIONS  [str]  "extensions of files to include (default: `[]`, aka, all extensions)"
   b content-exts CONTENTEXTS #{str} "print content for these extensions, default `#{}`"]
  (boot/with-pass-thru fileset
    (let [{:keys [content-exts] :as options} (merge +print-meta-defaults+ *opts*)
          entries (doall (map (:map-fn options) (filter-meta-by-ext fileset options)))]
      (pod/with-call-in @print-meta-pod
        (io.perun.print-meta/print-meta ~entries ~content-exts)))))

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

(def ^:private ^:deps mime-type-deps
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

(def ^:private ^:deps images-dimensions-deps
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

(def ^:private ^:deps images-resize-deps
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

(defn render-in-pod
  "Renders paths in `inputs`, using `render-form-fn` in `pod`

  `inputs` should be a map with keys that are fileset paths, and
  values that are the map argument that `render-form-fn` will be called with.

  Rendered results will be returned, with their `:path`s and `:canonical-url`s
  (if there is a valid `:base-url` in global metadata) set, and `tracer`
  added to `io.perun/trace`."
  [{:keys [task-name inputs tracer pod global-meta render-form-fn]}]
  (trace tracer
         (for [[path input] inputs]
           (do
             (perun/report-debug task-name "rendered page for path" path)
             (merge (pod/with-call-in @pod ~(render-form-fn input))
                    (pm/path-meta path global-meta))))))

(defn diff-inputs-meta
  "Returns a map containing the keys and values of `after` that do not exist
  or are different in `before`"
  [before after]
  (let [[x y z] (map (comp set keys) (data/diff before after))]
    (select-keys after (set/union y (set/intersection x z)))))

(defn diff-inputs-content
  "Returns a map containing the subset of `path-inputs` for which input file
  content has changed from `before-fs` to `after-fs`"
  [before-fs after-fs path-inputs]
  (let [changed-paths (-> (boot/fileset-diff before-fs after-fs :hash)
                          :tree keys set)]
    (->> path-inputs
         (filter (fn [[_ input]]
                   (pos? (count (set/intersection
                                 (:input-paths input)
                                 changed-paths)))))
         (into {}))))

(defn get-passthru-meta
  "Only relevant in cases where a task's output does not depend on metadata;
  takes care of copying possibly-changed incoming metadata, so that actual
  parsing can be bypassed when file content is unchanged."
  [{:keys [passthru-fn copy-inputs copy-files prev-path-inputs tracer global-meta]}]
  (when passthru-fn
    (let [copy-meta (into {} (map (juxt :path pm/+meta-key+) (vals copy-files)))
          passthru-meta (->> (passthru-fn copy-inputs tracer global-meta)
                             (map (juxt :path identity))
                             (into {}))
          prev-passthru-meta (->>
                              (-> prev-path-inputs
                                  (select-keys (keys copy-inputs))
                                  (passthru-fn tracer global-meta))
                              (map (juxt :path identity))
                              (into {}))]
      (reduce (fn [result [path m]]
                (conj result
                      (merge
                       (apply dissoc m (keys (prev-passthru-meta path)))
                       (passthru-meta path))))
              []
              copy-meta))))

(def ^:private ^:deps content-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]])

(defn content-task
  "Wrapper for input parsing tasks. Calls the return from `render-form-fn` on
  new or changed inputs returned from `paths-fn`, adds `tracer` to
  `:io.perun/trace` and writes files for subsequent tasks to process, if
  desired. Pass `pod` if one is needed for parsing. In cases where parsing only
  depends on the content of its input files rather than their metadata, set
  `passthru-fn` to handle setting changed metadata on files copied from the
  previous fileset. If input files should be removed from the fileset, set
  `rm-originals` to `true`."
  [{:keys [task-name render-form-fn paths-fn passthru-fn tracer pod rm-originals]}]
  (let [tmp  (boot/tmp-dir!)
        prev (atom {})
        pod (or pod (create-pod content-deps))]
    (fn [next-task]
      (fn [fileset]
        (let [refreshed (pod/with-call-in @pod (io.perun.render/update!))
              path-inputs (paths-fn fileset)
              uses-meta (not passthru-fn)
              render-inputs (cond
                              refreshed path-inputs
                              uses-meta (diff-inputs-meta (:path-inputs @prev)
                                                          path-inputs)
                              :else (diff-inputs-content (:input-fs @prev)
                                                         fileset
                                                         path-inputs))
              global-meta (pm/get-global-meta fileset)
              output-meta (render-in-pod {:task-name task-name
                                          :inputs render-inputs
                                          :tracer tracer
                                          :pod pod
                                          :global-meta global-meta
                                          :render-form-fn render-form-fn})
              _ (doseq [{:keys [path rendered]} output-meta]
                  (when rendered
                    (perun/report-debug task-name "writing page for path" path)
                    (perun/create-file tmp path rendered)))
              rm-files (if rm-originals
                         (->> (vals path-inputs)
                              (keep :input-paths)
                              (apply set/union)
                              (keep #(boot/tmp-get fileset %)))
                         [])
              copy-inputs (apply dissoc path-inputs (keys render-inputs))
              copy-files (select-keys (:tree (:output-fs @prev)) (keys copy-inputs))
              passthru-meta (get-passthru-meta {:passthru-fn passthru-fn
                                                :copy-inputs copy-inputs
                                                :copy-files copy-files
                                                :prev-path-inputs (:path-inputs @prev)
                                                :tracer tracer
                                                :global-meta global-meta})
              final-meta (into (map #(dissoc % :rendered :original) output-meta) passthru-meta)
              output-fs (-> fileset
                            (boot/rm rm-files)
                            (update-in [:tree] merge copy-files)
                            (commit tmp)
                            (pm/set-meta final-meta))]
          (reset! prev {:input-fs fileset :output-fs output-fs :path-inputs path-inputs})
          (perun/report-debug task-name "removed files" rm-files)
          (case (count render-inputs)
            0 nil
            1 (perun/report-info task-name "rendered new or changed file %s" (first (keys render-inputs)))
            (perun/report-info task-name "rendered %s new or changed files" (count render-inputs)))
          (case (count copy-files)
            0 nil
            1 (perun/report-info task-name "copied unchanged file %s" (first (keys copy-files)))
            (perun/report-info task-name "copied %s unchanged file(s)" (count copy-files)))
          (boot/empty-dir! tmp)
          (let [result-fs (next-task output-fs)]
            (pod/with-call-in @pod (io.perun.render/reset-refreshed!))
            result-fs))))))

(defn content-paths
  "Returns a map of path -> parser input for basic content tasks"
  [fileset {:keys [out-dir extensions out-ext meta] :as options}]
  (let [global-meta (pm/get-global-meta fileset)]
    (reduce (fn [result {:keys [path] :as entry}]
              (let [ext-pattern (re-pattern (str "(" (string/join "|" extensions) ")$"))
                    new-path (if out-ext
                               (->> out-ext
                                    (string/replace path ext-pattern)
                                    (perun/create-filepath out-dir))
                               (perun/create-filepath out-dir path))
                    path-meta (pm/path-meta path
                                            global-meta
                                            (boot/tmp-file (boot/tmp-get fileset path)))]
                (assoc result
                       new-path {:entry (merge {:original-path path}
                                               entry
                                               meta
                                               (when out-dir
                                                 {:out-dir out-dir})
                                               path-meta)
                                 :input-paths #{path}})))
            {}
            (filter-meta-by-ext fileset options))))

(defn content-passthru
  "Returns a map of path -> simulated parser output for basic content tasks;
  only used when file content hasn't changed, and so parsing can be skipped"
  [inputs tracer global-meta]
  (trace tracer
         (for [[path {:keys [entry]}] inputs]
           (merge entry (pm/path-meta path global-meta)))))

(def ^:private ^:deps yaml-metadata-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]
    [circleci/clj-yaml "0.5.5"]])

(def ^:private +yaml-metadata-defaults+
  {:filterer identity
   :extensions []})

(deftask yaml-metadata
  "Parse YAML metadata at the beginning of files

  This task is primarily intended for composing with other tasks.
  It will extract and parse any YAML data from the beginning of
  a file, and then overwrite that file with the YAML removed, and
  with the parsed data added as perun metadata."
  [_ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include (default: `[]`, aka, all extensions)"]
  (let [pod (create-pod yaml-metadata-deps)
        options (merge +yaml-metadata-defaults+ *opts*)]
    (content-task
     {:render-form-fn (fn [data] `(io.perun.yaml/parse-yaml ~data))
      :paths-fn #(content-paths % options)
      :passthru-fn content-passthru
      :task-name "yaml-metadata"
      :tracer :io.perun/yaml-metadata
      :rm-originals true
      :pod pod})))

(def ^:private ^:deps markdown-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]
    [org.pegdown/pegdown "1.6.0"]])

(def ^:private +markdown-defaults+
  {:out-dir "public"
   :out-ext ".html"
   :filterer identity
   :meta {:original true
          :include-rss true
          :include-atom true}})

(deftask markdown*
  "Parse markdown files

  This task will look for files ending with `md` or `markdown`
  and writes an HTML file that contains the result from
  processing the markdown file's content. It will _not_ parse
  YAML metadata at the head of the file."
  [d out-dir  OUTDIR str  "the output directory"
   x out-ext  OUTEXT str  "the output extension"
   _ filterer FILTER code "predicate to use for selecting entries (default: `identity`)"
   m meta     META   edn  "metadata to set on each entry; keys here will be overridden by metadata in each file"
   e md-exts  MDEXTS edn  "parsing extensions to be used by the markdown parser"]
  (let [pod (create-pod markdown-deps)
        options (merge +markdown-defaults+ *opts*)]
    (content-task
     {:render-form-fn (fn [data] `(io.perun.markdown/process-markdown ~data ~md-exts))
      :paths-fn #(content-paths % (assoc options :extensions [".md" ".markdown"]))
      :passthru-fn content-passthru
      :task-name "markdown"
      :tracer :io.perun/markdown
      :rm-originals true
      :pod pod})))

(deftask markdown
  "Parse markdown files with yaml front matter

  This task will look for files ending with `md` or `markdown`
  and writes an HTML file that contains the result from
  processing the markdown file's content. It will parse YAML
  metadata at the head of the file, and add any data found to
  the output's metadata."
  [d out-dir  OUTDIR str  "the output directory"
   _ filterer FILTER code "predicate to use for selecting entries (default: `identity`)"
   m meta     META   edn  "metadata to set on each entry; keys here will be overridden by metadata in each file"
   e md-exts  MDEXTS edn  "parsing extensions to be used by the markdown parser"]
  (let [{:keys [out-dir filterer meta md-exts]} (merge +markdown-defaults+ *opts*)]
    (comp (yaml-metadata :filterer filterer :extensions [".md" ".markdown"])
          (markdown* :out-dir out-dir :filterer filterer :meta meta :md-exts md-exts))))

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

(def ^:private ^:deps ttr-deps
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
                               (keep #(when-let [content (-> % boot/tmp-file slurp)]
                                        (let [meta (pm/meta-from-file fileset %)]
                                          (assoc meta :word-count (count (string/split content #"\s"))))))
                               (trace :io.perun/word-count))]
        (perun/report-info "word-count" "added word-count to %s files" (count updated-metas))
        (perun/report-debug "word-count" "counted words" (map :word-count updated-metas))
        (pm/set-meta fileset updated-metas)))))

(def ^:private ^:deps gravatar-deps
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
  "Renames a file so that the part before the extension matches the result of `slug-fn`"
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
                   (-> (str (:parent-path m) (:slug m))
                       perun/path-to-url
                       (str "/")
                       (string/replace (re-pattern (str "^" (:doc-root global-meta))) "")
                       perun/absolutize-url))
   :filterer identity
   :extensions [".html"]})

(deftask permalink
  "Moves a file so that its location matches the result of `permalink-fn`"
  [p permalink-fn PERMALINKFN code  "function to build permalink from TmpFile metadata"
   _ filterer     FILTER      code  "predicate to use for selecting entries (default: `identity`)"
   e extensions   EXTENSIONS  [str] "extensions of files to include"]
  (let [{:keys [permalink-fn] :as options} (merge +permalink-defaults+ *opts*)
        path-fn (fn [global-meta m]
                  (let [permalink (permalink-fn global-meta m)]
                    (str (:doc-root global-meta)
                         (perun/url-to-path (string/replace permalink #"/$" "/index.html")))))]
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

(def ^:private ^:deps sitemap-deps
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

(def ^:private ^:deps render-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]])

(def render-pod (delay (create-pod' render-deps)))

(defn- assert-renderer [sym]
  (assert (and (symbol? sym) (namespace sym))
          "Renderer must be a fully qualified symbol, i.e. 'my.ns/fun"))

(defn render-task
  "Handles common rendering task orchestration

  `paths-fn` takes a fileset as its only argument"
  [{:keys [task-name paths-fn renderer tracer rm-originals]}]
  (assert-renderer renderer)
  (content-task
   {:render-form-fn (fn [meta] `(io.perun.render/render ~renderer ~meta))
    :paths-fn paths-fn
    :task-name task-name
    :tracer tracer
    :pod render-pod
    :rm-originals rm-originals}))

(def ^:private +render-defaults+
  {:out-dir "public"
   :filterer identity
   :extensions [".html"]})

(deftask render
  "Render individual pages from input files

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
  (let [{:keys [renderer] :as options} (merge +render-defaults+ *opts*)]
    (letfn [(render-paths [fileset]
              (let [entries (filter-meta-by-ext fileset options)]
                (reduce
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
                                             :entry   new-entry
                                             :input-paths #{path}})))
                 {}
                 entries)))]
      (render-task {:task-name "render"
                    :paths-fn render-paths
                    :renderer renderer
                    :tracer :io.perun/render
                    :rm-originals true}))))

(def ^:private +static-defaults+
  {:out-dir "public"
   :page "index.html"
   :meta {}})

(deftask static
  "Render an individual page solely from a render function

   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entry`, the entry to be rendered"
  [o out-dir  OUTDIR   str "the output directory"
   r renderer RENDERER sym "page renderer (fully qualified symbol resolving to a function)"
   p page     PAGE     str "static result page path"
   m meta     META     edn "metadata to set on the static entry"]
  (let [{:keys [out-dir renderer page meta]} (merge +static-defaults+ *opts*)
        path (perun/create-filepath out-dir page)
        static-path (fn [fileset]
                      {path {:meta (pm/get-global-meta fileset)
                             :entry (assoc meta :path path)}})]
    (render-task {:task-name "static"
                  :paths-fn static-path
                  :renderer renderer
                  :tracer :io.perun/static})))

(defn- grouped-paths
  "Produces path maps of the shape required by `render-to-paths`, based
  on the provided `fileset` and `options`."
  [task-name fileset {:keys [grouper sortby comparator out-dir] :as options}]
  (let [global-meta (pm/get-global-meta fileset)
        paths (grouper (filter-meta-by-ext fileset options))]
    (if (seq paths)
      (reduce
       (fn [result [path {:keys [entry entries]}]]
         (let [sorted    (->> entries
                              (sort-by sortby comparator)
                              (map #(assoc % :content (->> (:path %)
                                                           (boot/tmp-get fileset)
                                                           boot/tmp-file
                                                           slurp))))
               new-path  (perun/create-filepath out-dir path)
               new-entry (merge entry
                                {:out-dir out-dir}
                                (pm/path-meta path global-meta))]
           (assoc result new-path {:meta global-meta
                                   :entry new-entry
                                   :entries (vec sorted)
                                   :input-paths (set sorted)})))
       {}
       paths)
      (do
        (perun/report-info task-name (str task-name " found nothing to render"))
        []))))

(defn assortment-task
  "Handles common assortment task orchestration

  `task-name` is used for log messages. `tracer` is a keyword that gets added
  to the `:io.perun/trace` metadata. `grouper` is a function that takes a seq
  of entries and returns a map of paths to render data (see docstring for
  `assortment` for more info)"
  [{:keys [task-name comparator filterer sortby grouper meta renderer tracer] :as options*}]
  (cond (not (fn? comparator))
        (u/fail (str task-name " task :comparator option should implement Fn\n"))
        (not (ifn? filterer))
        (u/fail (str task-name " task :filterer option value should implement IFn\n"))
        (not (ifn? sortby))
        (u/fail (str task-name " task :sortby option value should implement IFn\n"))
        (not (ifn? grouper))
        (u/fail (str task-name " task :grouper option value should implement IFn\n"))
        :else
        (let [;; Make sure task-level metadata gets added to each entry
              meta-grouper (fn [entries]
                             (->> entries
                                  grouper
                                  (map (fn [[path data]]
                                         [path (update-in data [:entry] #(merge meta %))]))
                                  (into {})))
              options (assoc options* :grouper meta-grouper)]
          (render-task {:task-name task-name
                        :paths-fn #(grouped-paths task-name % options)
                        :renderer renderer
                        :tracer tracer}))))

(def ^:private +assortment-defaults+
  {:out-dir "public"
   :filterer identity
   :extensions [".html"]
   :sortby :date-published
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
  (let [options (merge +assortment-defaults+
                       *opts*
                       {:task-name "assortment"
                        :tracer :io.perun/assortment
                        :grouper (or grouper #(-> {"index.html" {:entries %}}))})]
    (assortment-task options)))

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
  (let [p (or page "index.html")
        options (merge +collection-defaults+
                       (dissoc *opts* :page)
                       {:task-name "collection"
                        :tracer :io.perun/collection
                        :grouper #(-> {p {:entries %}})})]
    (assortment-task options)))

(def ^:private +tags-defaults+
  {:out-dir "public"
   :out-ext ".html"
   :filterer identity
   :extensions [".html"]
   :sortby :date-published
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
   x out-ext    OUTEXT     str   "the output extension"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol resolving to a function)"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   s sortby     SORTBY     code  "sort entries by function"
   c comparator COMPARATOR code  "sort by comparator function"
   m meta       META       edn   "metadata to set on each collection entry"]
  (let [{:keys [out-ext] :as options*} (merge +tags-defaults+ *opts*)
        grouper (fn [entries]
                  (->> entries
                       (mapcat (fn [entry]
                                 (map #(-> [% entry]) (:tags entry))))
                       (reduce (fn [result [tag entry]]
                                 (let [path (str tag out-ext)]
                                   (-> result
                                       (update-in [path :entries] conj entry)
                                       (assoc-in [path :entry :tag] tag))))
                               {})))
        options (assoc options*
                       :task-name "tags"
                       :tracer :io.perun/tags
                       :grouper grouper)]
    (assortment-task options)))

(defn page-grouper-fn
  [{:keys [sortby comparator page-size filename-fn]}]
  (fn [entries]
    (let [pages (->> entries
                     (sort-by sortby comparator)
                     (partition-all page-size))]
      (->> pages
           (map-indexed
            #(-> [(filename-fn (inc %1))
                  {:entry (merge {:page (inc %1)
                                  :first-page (filename-fn 1)
                                  :last-page (filename-fn (count pages))}
                                 (when (pos? %1)
                                   {:prev-page (filename-fn %1)})
                                 (when (< %1 (dec (count pages)))
                                   {:next-page (filename-fn (inc (inc %1)))}))
                   :entries %2}]))
           (into {})))))

(def ^:private +paginate-defaults+
  {:out-dir "public"
   :out-ext ".html"
   :page-size 10
   :filterer identity
   :extensions [".html"]
   :sortby :date-published
   :comparator #(compare %2 %1)
   :slug-fn #(str "page-" %)})

(deftask paginate
  "Render multiple collections
   The symbol supplied as `renderer` should resolve to a function
   which will be called with a map containing the following keys:
    - `:meta`, global perun metadata
    - `:entry`, the metadata for this collection
    - `:entries`, all entries

   Entries can optionally be filtered by supplying a function
   to the `filterer` option.

   The `sortby` function can be used for ordering entries before rendering."
  [o out-dir    OUTDIR     str   "the output directory"
   x out-ext    OUTEXT     str   "the output extension"
   f slug-fn    SLUGFN     code  "takes page num, returns a slug (default: page-1, page-2, etc)"
   p page-size  PAGESIZE   int   "the number of entries to include in each page (default: `10`)"
   r renderer   RENDERER   sym   "page renderer (fully qualified symbol resolving to a function)"
   _ filterer   FILTER     code  "predicate to use for selecting entries (default: `identity`)"
   e extensions EXTENSIONS [str] "extensions of files to include"
   s sortby     SORTBY     code  "sort entries by function"
   c comparator COMPARATOR code  "sort by comparator function"
   m meta       META       edn   "metadata to set on each collection entry"]
  (let [{:keys [slug-fn out-ext] :as options**} (merge +paginate-defaults+
                                                       *opts*
                                                       {:task-name "paginate"
                                                        :tracer :io.perun/paginate})
        options* (assoc options** :filename-fn #(str (slug-fn %) out-ext))
        options (assoc options* :grouper (page-grouper-fn options*))]
    (assortment-task options)))

(defn atom-paths
  [fileset options]
  (let [{:keys [site-title base-url author]} (merge (pm/get-global-meta fileset)
                                                    options)
        paths (grouped-paths "atom-feed" fileset options)
        missing-title (not (seq site-title))
        entries (mapcat :entries (vals paths))
        dupe-uuids (->> (keep :uuid entries)
                        frequencies
                        (filter (fn [[_ num]] (> num 1)))
                        (map first)
                        seq)
        no-uuid (seq (remove :uuid entries))
        no-author (when-not author (seq (remove :author entries)))]
    (perun/assert-base-url base-url)
    (when missing-title
      (u/fail "Atom XML requires non-empty site-title\n"))
    (doseq [uuid dupe-uuids]
      (let [dupe-paths (map :path (filter #(= uuid (:uuid %)) entries))]
        (u/fail
         (format (str "The same uuid is assigned to these files: %s. You may "
                      "find these fresh uuids handy: %s\n")
                 (string/join ", " dupe-paths)
                 (->> (repeatedly #(str (java.util.UUID/randomUUID)))
                      (take (dec (count dupe-paths)))
                      (string/join ", "))))))
    (doseq [{:keys [path]} no-uuid]
      (u/fail
       (format (str "Atom XML requires that each post has a unique uuid. %s is "
                    "missing one. If you need one, use this: %s\n")
               path
               (str (java.util.UUID/randomUUID)))))
    (doseq [{:keys [path]} no-author]
      (u/fail
       (format (str "Atom XML requires that each post has an author name. "
                    "%s is missing one\n")
               path)))
    (when-not (or missing-title dupe-uuids no-uuid no-author)
      paths)))

(def ^:private ^:deps atom-deps
  '[[org.clojure/tools.namespace "0.3.0-alpha3"]
    [org.clojure/data.xml "0.0.8"]
    [clj-time "0.12.0"]])

(def ^:private +atom-defaults+
  {:filename "atom.xml"
   :page-size 10
   :filterer :include-atom
   :extensions [".html"]
   :out-dir "public"})

(deftask atom-feed
  "Generate Atom feed"
  [f filename    FILENAME    str   "generated Atom feed filename"
   p page-size   PAGESIZE    int   "the number of entries to include in each page (default: `10`)"
   _ filterer    FILTER      code  "predicate to use for selecting entries (default: `:include-atom`)"
   e extensions  EXTENSIONS  [str] "extensions of files to include in the feed"
   o out-dir     OUTDIR      str   "the output directory"
   t site-title  TITLE       str   "feed title"
   s subtitle    SUBTITLE    str   "feed subtitle"
   d description DESCRIPTION str   "feed description"
   l base-url    LINK        str   "feed link"]
  (let [{:keys [filename] :as options*} (merge +atom-defaults+
                                               *opts*
                                               {:sortby :date-published
                                                :comparator #(compare %2 %1)})
        filename-fn (fn [i]
                      (case i
                        1 filename
                        (str (perun/filename filename) "-" i
                             "." (perun/extension filename))))
        options (assoc options*
                       :grouper (page-grouper-fn (assoc options* :filename-fn filename-fn)))]
    (content-task
     {:render-form-fn (fn [data] `(io.perun.atom/generate-atom ~data))
      :paths-fn #(atom-paths % options)
      :task-name "atom-feed"
      :tracer :io.perun/atom-feed
      :pod (create-pod atom-deps)})))

(def ^:private ^:deps rss-deps
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

(def +inject-scripts-defaults+
  {:extensions [".html"]})

(deftask inject-scripts
  "Inject JavaScript scripts into html files.
   Use either filter to include only files matching or remove to
   include only files not matching regex."
  [s scripts    JAVASCRIPT #{str}   "JavaScript files to inject as <script> tags in <head>."
   f filter     RE         #{regex} "Regexes to filter HTML files"
   r remove     RE         #{regex} "Regexes to blacklist HTML files with"
   e extensions EXTENSIONS [str]    "extensions of files to include"]
  (let [{:keys [scripts filter remove extensions]} (merge +inject-scripts-defaults+ *opts*)
        filter (cond
                 filter #(boot/by-re filter %)
                 remove #(boot/by-re remove % true)
                 :else identity)]
    (letfn [(inject-paths [fileset]
              (let [all-files (boot/ls fileset)
                    files (->> all-files
                               filter
                               (boot/by-ext extensions))
                    scripts-contents (->> all-files
                                          (boot/by-path scripts)
                                          (map (comp slurp boot/tmp-file)))]
                (into {} (for [f files]
                           [(:path f) {:entry (pm/meta-from-file fileset f)
                                       :scripts scripts-contents
                                       :input-paths (conj scripts (:path f))}]))))]
      (content-task
       {:render-form-fn (fn [data] `(io.perun.contrib.inject-scripts/inject-scripts ~data))
        :paths-fn inject-paths
        :passthru-fn content-passthru
        :task-name "inject-scripts"
        :tracer :io.perun/inject-scripts}))))
