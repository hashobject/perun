(ns io.perun.contrib.asciidoctor
  "AsciidoctorJ based converter from Asciidoc to HTML"
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

;; TODO integrate all options and defaults into a single map, and expose to the perun.clj file

(defn new-adoc-container
  "Creates a new AsciidoctorJ (JRuby) container, based on the normalized options
   provided."
  [n-opts]
  (let [libraries (get n-opts "libraries")]
    ; (AsciidoctorContainer. (Asciidoctor$Factory/create) gempath libraries)))
    (Asciidoctor$Factory/create (str (get n-opts "gempath")))))
;; TODO add desired libraries

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
;; TODO incorporate options into container creation

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
  "Responsible for parsing all provided asciidoc files. The actual parsing is
   dispatched."
  [asciidoc-files options]
  (let [updated-files (doall (map #(process-file % options) asciidoc-files))]
    (perun/report-info "asciidoctor" "parsed %s asciidoc files" (count asciidoc-files))
    updated-files))
