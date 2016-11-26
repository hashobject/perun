(ns io.perun.content.asciidoc
  (:require [io.perun.core    :as perun]
            [io.perun.content :as content]
            [clojure.java.io  :as io])
  (:import [org.asciidoctor Asciidoctor$Factory]))

;; Copied from https://github.com/ruedigergad/clj-assorted-utils/blob/master/src/clj_assorted_utils/util.clj
(defn convert-from-clojure-to-java
  "Converts the given Clojure specific data structure (list, map, set, vector) into the equivalent \"pure\" Java data structure.
   The mapping is as follows: list and vector -> ArrayList, map -> HashMap, set -> HashSet.
   Nested data structures will be converted recursively."
  [input]
  (cond
    (or
      (list? input)
      (vector? input)) (let [out (java.util.ArrayList.)]
                         (doseq [in-element input]
                           (if (coll? in-element)
                             (.add out (convert-from-clojure-to-java in-element))
                             (.add out in-element)))
                         out)
    (map? input) (let [out (java.util.HashMap.)]
                   (doseq [in-element input]
                     (if (coll? (val in-element))
                       (.put out (key in-element) (convert-from-clojure-to-java (val in-element)))
                       (.put out (key in-element) (val in-element))))
                   out)
    (set? input) (let [out (java.util.HashSet.)]
                   (doseq [in-element input]
                     (if (coll? in-element)
                       (.add out (convert-from-clojure-to-java in-element))
                       (.add out in-element)))
                   out)))

(defn asciidoc-to-html [file-content options]
  (let [processor (Asciidoctor$Factory/create)
        asciidoc-content (content/remove-metadata file-content)]
    (.convert processor asciidoc-content (or (convert-from-clojure-to-java options)
                                             (java.util.HashMap.)))))

(defn process-file [file options]
  (perun/report-debug "content" "processing asciidoc" (:filename file))
  (let [file-content (-> file :full-path io/file slurp)
        adoc-metadata (content/parse-file-metadata file-content)
        html (asciidoc-to-html file-content options)]
    (merge adoc-metadata {:content html} file)))

(defn parse-asciidoc [asciidoc-files options]
  (let [updated-files (doall (map #(process-file % options) asciidoc-files))]
    (perun/report-info "content" "parsed %s asciidoc files" (count asciidoc-files))
    updated-files))
