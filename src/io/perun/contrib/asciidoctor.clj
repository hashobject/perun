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
   level of any nesting structure."
  [m]
  (reduce-kv #(assoc %1 (name %2) %3) {} m))

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
   tasks."
  [file-content]
  (md/parse-file-metadata file-content))
;; TODO include asciidoctor based metadata including attributes

(defn asciidoc-to-html
  "Converts a given string of asciidoc into HTML. The normalized options that
   can be provided, influence the behavior of the conversion."
  [file-content n-opts]
  (let [container (new-adoc-container n-opts)
        options   (-> (select-keys ["header_footer" "attributes"] n-opts)
                      (assoc "backend" "html5"))]
    (.convert container (md/remove-metadata file-content) options)))

(defn process-file
  "Parses the content of a single file and associates the available metadata to
   the resulting html string. The HTML conversion is dispatched."
  [file options]
  (perun/report-debug "asciidoctor" "processing asciidoc" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        ad-metadata  (parse-file-metadata file-content)
        n-opts       (normalize-options options)
        html         (asciidoc-to-html file-content n-opts)]
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
