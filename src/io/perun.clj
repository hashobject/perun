(ns io.perun
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :as u]
            [clojure.string :as string]
            [clojure.test :as test]
            [io.perun.core :as perun :refer [get-perun-meta with-perun-meta]]))

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
  []
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
                               (io.perun.markdown/parse-markdown ~markdown-files))
            initial-metadata @prev-meta
            final-metadata   (merge initial-metadata parsed-metadata)
            final-metadata   (apply dissoc final-metadata removed-files)
            fs-with-meta     (with-perun-meta fileset final-metadata)]
        (reset! prev-fs fileset)
        (reset! prev-meta final-metadata)
        fs-with-meta))))

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
            fs-with-meta  (with-perun-meta fileset updated-files)]
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
      (let [files (get-perun-meta fileset)
            updated-files (pod/with-call-in @pod
                            (io.perun.gravatar/find-gravatar ~files ~source-key ~target-key))
            fs-with-meta  (with-perun-meta fileset updated-files)]
        (u/dbug "Find gravatars:\n%s\n"
                (pr-str (map target-key (vals updated-files))))
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
  (perun/absolutize-url (str (:slug metadata) "/index.html")))

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
  (let [pod     (create-pod sitemap-deps)
        tmp     (boot/tmp-dir!)
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
  (let [pod     (create-pod rss-deps)
        tmp     (boot/tmp-dir!)
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

(defn- render-in-pod [pod sym file]
  {:pre [(symbol? sym) (namespace sym)]}
  (pod/with-eval-in pod
    (require '~(symbol (namespace sym)))
    ((resolve '~sym) ~file)))

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
            files (get-perun-meta fileset)]
        (doseq [[filename file] files]
          (let [html          (render-in-pod pod renderer file)
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
   :sortby (fn [file] (:date-published file))
   :comparator (fn [i1 i2] (compare i2 i1))})

(deftask collection
  "Render collection files"
  [o out-dir    OUTDIR     str  "The output directory"
   r renderer   RENDERER   sym  "Page renderer. Fully qualified symbol resolving to a function."
   f filterer   FILTER     code "Filter function"
   s sortby     SORTBY     code "Sort by function"
   c comparator COMPARATOR code "Sort by comparator function"
   p page       PAGE       str  "Collection result page path"]
  (let [pods      (wrap-pool (pod/pod-pool (boot/get-env)))
        tmp       (boot/tmp-dir!)
        options   (merge +collection-defaults+ *opts*)]
    (cond (not (test/function? (:comparator options)))
              (u/fail "collection task :comparator option should be a function\n")
          (not (test/function? (:filterer options)))
              (u/fail "collection task :filterer option should be a function\n")
          (not (test/function? (:sortby options)))
              (u/fail "collection task :sortby option should be a function\n")
          :else
            (boot/with-pre-wrap fileset
              (let [pod            (pods fileset)
                    files          (vals (get-perun-meta fileset))
                    filtered-files (filter (:filterer options) files)
                    sorted-files   (vec (sort-by (:sortby options) (:comparator options) filtered-files))
                    html           (render-in-pod pod renderer sorted-files)
                    page-filepath  (perun/create-filepath (:out-dir options) page)]
                (perun/create-file tmp page-filepath html)
                (u/info (str "Render collection " page "\n"))
                (commit fileset tmp))))))
