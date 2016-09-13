(ns io.perun.contrib.asciidoctor
  "AsciidoctorJ based converter from Asciidoc to HTML"
  (:require [io.perun.core     :as perun]
            [clojure.java.io   :as io]
            [clojure.string    :as str]
            [clj-yaml.core     :as yml]
            [io.perun.markdown :as md])
  (:import [org.asciidoctor Asciidoctor Asciidoctor$Factory]
           org.asciidoctor.internal.JRubyAsciidoctor))

(def adoc-container-defaults {:gempath   ""
                              :libraries '("asciidoctor-diagram")})
;; TODO integrate all options and defaults into a single map, and expose to the perun.clj file

(defn new-adoc-container [& {:keys [gempath libraries]}]
  (let [defaults  adoc-container-defaults
        gempath   (or gempath   (:gempath   defaults))
        libraries (or libraries (:libraries defaults))]
    ; (AsciidoctorContainer. (Asciidoctor$Factory/create) gempath libraries)))
    (Asciidoctor$Factory/create gempath)))
;; TODO add desired libraries

(defn parse-file-metadata [file-content]
  (md/parse-file-metadata file-content))
;; TODO include asciidoctor based metadata including attributes

(defn asciidoc-to-html [file-content options]
  (let [container (new-adoc-container)
        opts {"backend" "html5"}]
    (.convert container file-content opts)))
;; TODO incorporate options into container creation

(defn process-file [file options]
  (perun/report-debug "asciidoctor" "processing asciidoc" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        ad-metadata (parse-file-metadata file-content)
        html (asciidoc-to-html file-content options)]
    (merge ad-metadata {:content html} file)))

(defn parse-asciidoc [asciidoc-files options]
  (let [updated-files (doall (map #(process-file % options) asciidoc-files))]
    (perun/report-info "asciidoctor" "parsed %s asciidoc files" (count asciidoc-files))
    updated-files))
