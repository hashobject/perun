(ns io.perun.content.markdown
  (:require [io.perun.core    :as perun]
            [io.perun.content :as content]
            [clojure.java.io  :as io])
  (:import [org.pegdown PegDownProcessor Extensions]))

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

(defn markdown-to-html [file-content options]
  (let [processor (PegDownProcessor. (extensions-map->int (:extensions options)))]
    (->> file-content
         content/remove-metadata
         char-array
         (.markdownToHtml processor))))

(defn process-file [file options]
  (perun/report-debug "content" "processing markdown" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        md-metadata (content/parse-file-metadata file-content)
        html (markdown-to-html file-content options)]
    (merge md-metadata {:content html} file)))

(defn parse-markdown [markdown-files options]
  (let [updated-files (doall (map #(process-file % options) markdown-files))]
    (perun/report-info "content" "parsed %s markdown files" (count markdown-files))
    updated-files))
