(ns io.perun.asciidoctor
  (:require [io.perun.core :as perun]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clojure.java.io :as io])
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

(def container
  (Asciidoctor$Factory/create ""))

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
          :canonical-url :extension :filename :full-path :parent-path :permalink
          :short-filename :slug))

(defn parse-file-metadata
  "Processes the asciidoctor content and extracts all the attributes."
  [adoc-content]
  (->> (.readDocumentStructure container adoc-content {})
       (.getHeader)
       (.getAttributes)
       attributes->meta
       protect-meta))

(defn asciidoctor-to-html [file-content attributes]
  (let [options (if attributes
                  {"attributes" attributes}
                  {})]
    (.convert container file-content options)))

(defn process-asciidoctor [{:keys [entry]}]
  (perun/report-debug "asciidoctor" "processing asciidoctor" (:filename entry))
  (let [file-content (-> entry :full-path io/file slurp)
        attributes   (meta->attributes entry)
        html         (asciidoctor-to-html file-content attributes)
        meta         (parse-file-metadata file-content)]
    (merge (assoc entry :rendered html) meta)))
