(ns io.perun.contrib.asciidoctor
  "AsciidoctorJ based converter from Asciidoc to HTML."
  (:require [io.perun.core     :as perun]
            [clojure.java.io   :as io]
            [clojure.string    :as str]
            [clj-yaml.core     :as yml]
            [io.perun.markdown :as md])
  (:import [org.asciidoctor Asciidoctor Asciidoctor$Factory]))

(defn keywords->names
  "Converts a map with keywords to a map with named keys. Only handles the top
   level of any nested structure."
  [m]
  (reduce-kv #(assoc %1 (name %2) %3) {} m))

(defn names->keywords
  "Converts a map with named keys to a map with keywords. Only handles the top
   level of any nested structure."
   [m]
   (reduce-kv #(assoc %1 (keyword %2) %3) {} m))

(defn normalize-options
  "Takes the options for the Asciidoctor parser and puts the in the format
   appropriate for handling by the downstream functions. Mostly to better suit
   the parsing by the AsciidoctorJ library."
  [clj-opts]
  (let [atr  (-> (:attributes clj-opts)
                 (keywords->names)
                 (java.util.HashMap.))
        opts (assoc clj-opts :attributes atr)]
    (keywords->names opts)))

(defn new-adoc-container
  "Creates a new AsciidoctorJ (JRuby) container, based on the normalized options
   provided."
  [n-opts]
  (let [acont (Asciidoctor$Factory/create (str (get n-opts "gempath")))]
    (doto acont (.requireLibraries (into '() (get n-opts "libraries"))))))

(defn parse-file-metadata
  "Read the file-content and derive relevant metadata for use in other Perun
   tasks. The document is read in its entirety (.readDocumentStructure instead
   of .readDocumentHeader) to have the results of the options reflected into the
   resulting metadata. As the document is rendered again, the time-based
   attributes will vary from the asciidoc-to-html convertion (doctime,
   docdatetime, localdate, localdatetime, localtime)."
  [container file-content n-opts]
  (let [frontmatter (md/parse-file-metadata file-content)
        attributes  (->> (.readDocumentStructure container file-content n-opts)
                         (.getHeader)
                         (.getAttributes)
                         (into {})
                         (names->keywords))]
    (merge frontmatter attributes)))
;; TODO align attribute keywords with perun keywords
;; TODO perhaps use dedicated functions for getting the title and author info

(defn asciidoc-to-html
  "Converts a given string of asciidoc into HTML. The normalized options that
   can be provided, influence the behavior of the conversion."
  [container file-content n-opts]
  (let [options   (-> (select-keys ["header_footer" "attributes"] n-opts)
                      (assoc "backend" "html5"))]
    (.convert container (md/remove-metadata file-content) options)))

(defn process-file
  "Parses the content of a single file and associates the available metadata to
   the resulting html string. The HTML conversion is dispatched."
  [file options]
  (perun/report-debug "asciidoctor" "processing asciidoc" (:filename file))
  (let [n-opts       (normalize-options options)
        container    (new-adoc-container n-opts)
        file-content (-> file :full-path io/file slurp)
        ad-metadata  (parse-file-metadata container file-content n-opts)
        html         (asciidoc-to-html container file-content n-opts)]
    (merge ad-metadata {:content html} file)))

(defn parse-asciidoc
  "The main function of `io.perun.contrib.asciidoctor`. Responsible for parsing
   all provided asciidoc files. The actual parsing is dispatched. It accepts a
   boot fileset and a map of options.

   The map of options typically includes an array of libraries and an array of attributes: {:libraries [] :attributes {}}. Libraries are loaded from the AsciidoctorJ project, and can be loaded specifically (\"asciidoctor-diagram/ditaa\") or more broadly (\"asciidoctor-diagram\"). Attributes can be set freely, although a large set
   has been predefined in the Asciidoctor project to configure rendering options
   or set meta-data."
  [asciidoc-files options]
  (let [updated-files (doall (map #(process-file % options) asciidoc-files))]
    (perun/report-info "asciidoctor" "parsed %s asciidoc files" (count asciidoc-files))
    updated-files))
