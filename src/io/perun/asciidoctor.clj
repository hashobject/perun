(ns io.perun.asciidoctor
  (:require [io.perun.core :as perun]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [clojure.string :as str])
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

(defn container
  "Creates a new Asciidoctor container, with or without the
  `asciidoctor-diagram` library."
  [diagram]
  (doto (Asciidoctor$Factory/create "")
    (.requireLibraries (if diagram
                         '("asciidoctor-diagram")
                         '()))))

(defn meta->attributes
  "Takes the Perun meta and converts it to a collection of attributes, which can
  be handed to the AsciidoctorJ process."
  [meta]
  (-> meta
      keywords->names
      (java.util.HashMap.)))

(defn parse-date
  "Tries to parse a date string into a DateTime object"
  [date]
  (when date
    (if-let [parsed (tc/to-date date)]
      parsed
      (perun/report-info "asciidoctor" "failed to parse date %s" date))))

(defn attributes->meta
  "Add duplicate entries for the metadata keys gathered from the AsciidoctorJ
  parsing using keys that adhere to the Perun specification of keys. The native
  AsciidoctorJ keys are still available."
  [attributes]
  (let [meta (names->keywords (into {} attributes))]
    (merge meta
           {:author-email   (:email     meta)
            :title          (:doctitle  meta)
            :date-published (parse-date (:revdate meta))})))

(defn protect-meta
  "Strip keywords from metadata that are being used by Perun to properly
  function."
  [meta]
  (dissoc meta
          :canonical-url :content :extension :filename :full-path :parent-path
          :path :permalink :short-filename :slug))

(defn options
  "Create an options object"
  [safe attributes outdir]
  {:pre [(number? safe)]}
  {"attributes" (if attributes
                  attributes
                  (java.util.HashMap.))
   "safe"       (int safe)
   "base_dir"   outdir})

(defn parse-file-metadata
  "Processes the asciidoctor content and extracts all the attributes."
  [container adoc-content options]
  (->> (.readDocumentStructure container adoc-content options)
       (.getHeader)
       (.getAttributes)
       attributes->meta
       protect-meta))

(defn asciidoctor-to-html [container file-content options]
  (.convert container file-content options))

(defn strip-trailing-slash
  [path]
  (str/replace path "/^" ""))

(defn process-asciidoctor [out-dir img-dir diagram safe {:keys [entry]}]
  (perun/report-debug "asciidoctor" "processing asciidoctor" (:filename entry))
  (let [outdir       (str/replace (str/join "/" [img-dir out-dir (:parent-path entry)]) "/^" "")
        _            (.mkdirs (clojure.java.io/file outdir))
        file-content (-> entry :full-path io/file slurp)
        attributes   (meta->attributes (assoc entry :outdir outdir))
        opts         (options safe attributes outdir)
        cont         (container diagram)
        html         (asciidoctor-to-html cont file-content opts)
        meta         (parse-file-metadata cont file-content opts)]
    (merge (assoc entry :rendered html) meta)))
