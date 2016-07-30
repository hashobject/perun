(ns io.perun.markdown
  (:require [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [clj-yaml.core   :as yaml]
            [clojure.walk    :as walk])
  (:import [org.pegdown PegDownProcessor Extensions]
           [flatland.ordered.map OrderedMap]
           [flatland.ordered.set OrderedSet]))

;; Extension handling has been copied from endophile.core
;; See https://github.com/sirthias/pegdown/blob/master/src/main/java/org/pegdown/Extensions.java
;; for descriptions
(def extensions
  {:smarts               Extensions/SMARTS
   :quotes               Extensions/QUOTES
   :smartypants          Extensions/SMARTYPANTS
   :abbreviations        Extensions/ABBREVIATIONS
   :hardwraps            Extensions/HARDWRAPS
   :autolinks            Extensions/AUTOLINKS
   :tables               Extensions/TABLES
   :definitions          Extensions/DEFINITIONS
   :fenced-code-blocks   Extensions/FENCED_CODE_BLOCKS
   :wikilinks            Extensions/WIKILINKS
   :strikethrough        Extensions/STRIKETHROUGH
   :anchorlinks          Extensions/ANCHORLINKS
   :all                  Extensions/ALL
   :suppress-html-blocks Extensions/SUPPRESS_HTML_BLOCKS
   :supress-all-html     Extensions/SUPPRESS_ALL_HTML
   :atxheaderspace       Extensions/ATXHEADERSPACE
   :forcelistitempara    Extensions/FORCELISTITEMPARA
   :relaxedhrules        Extensions/RELAXEDHRULES
   :tasklistitems        Extensions/TASKLISTITEMS
   :extanchorlinks       Extensions/EXTANCHORLINKS
   :all-optionals        Extensions/ALL_OPTIONALS
   :all-with-optionals   Extensions/ALL_WITH_OPTIONALS})

(defn extensions-map->int [opts]
  (->> opts
       (merge {:autolinks true
               :strikethrough true
               :fenced-code-blocks true
               :extanchorlinks true})
       (filter val)
       keys
       (map extensions)
       (apply bit-or 0)
       int))

(defn substr-between
  "Find string that is nested in between two strings. Return first match.
  Copied from https://github.com/funcool/cuerdas"
  [s prefix suffix]
  (cond
    (nil? s) nil
    (nil? prefix) nil
    (nil? suffix) nil
    :else
    (some-> s
            (str/split prefix)
            second
            (str/split suffix)
            first)))

(defn normal-colls
  "Clj-yaml keeps order of map properties by using ordered maps. These are inconvenient
  for us as the ordered library is not necessarily available in other pods."
  [x]
  (walk/postwalk
    (fn [y]
      (cond
        (instance? OrderedMap y) (into {} y)
        (instance? OrderedSet y) (into #{} y)
        :else y))
    x))

(defn parse-file-metadata [file-content]
  (if-let [metadata-str (substr-between file-content #"---\n" #"---\n")]
    (if-let [parsed-yaml (normal-colls (yaml/parse-string metadata-str))]
      ; we use `original` file flag to distinguish between generated files
      ; (e.x. created those by plugins)
      (assoc parsed-yaml :original true)
      {:original true})
    {:original true}))

(defn remove-metadata [content]
  (let [splitted (str/split content #"---\n" 3)]
    (if (> (count splitted) 2)
      (first (drop 2 splitted))
      content)))

(defn markdown-to-html [file-content options]
  (let [processor (PegDownProcessor. (extensions-map->int (:extensions options)))]
    (->> file-content
         remove-metadata
         char-array
         (.markdownToHtml processor))))

(defn process-file [file options]
  (perun/report-debug "markdown" "processing markdown" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        md-metadata (parse-file-metadata file-content)
        html (markdown-to-html file-content options)]
    (merge md-metadata {:content html} file)))

(defn parse-markdown [markdown-files options]
  (let [updated-files (doall (map #(process-file % options) markdown-files))]
    (perun/report-info "markdown" "parsed %s markdown files" (count markdown-files))
    updated-files))
