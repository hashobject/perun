(ns io.perun.markdown
  (:require [io.perun.core   :as perun]
            [clojure.java.io :as io])
  (:import [com.vladsch.flexmark.html HtmlRenderer]
           [com.vladsch.flexmark.parser Parser]
           [com.vladsch.flexmark.profiles.pegdown Extensions PegdownOptionsAdapter]))

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
  (let [flexmark-opts (-> options
                          :extensions
                          extensions-map->int
                          PegdownOptionsAdapter/flexmarkOptions)
        parser (.build (Parser/builder flexmark-opts))
        renderer (.build (HtmlRenderer/builder flexmark-opts))]
    (->> file-content
         (.parse parser)
         (.render renderer))))

(defn process-file [file options]
  (perun/report-debug "markdown" "processing markdown" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        html (markdown-to-html file-content (:options options))]
    (merge (:meta options) {:parsed html} file)))

(defn parse-markdown [markdown-files options]
  (let [updated-files (doall (map #(process-file % options) markdown-files))]
    (perun/report-info "markdown" "parsed %s markdown files" (count markdown-files))
    updated-files))
