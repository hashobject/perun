(ns io.perun.markdown
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [endophile.core  :as endophile]
            [clj-yaml.core   :as yaml]))

(defn extract-between [s prefix suffix]
  (some-> s
          (str/split prefix)
          second
          (str/split suffix)
          first))

(defn parse-file-metadata [file-content]
  (if-let [metadata-str (extract-between file-content #"---\n" #"---\n")]
    (if-let [parsed-yaml (yaml/parse-string metadata-str)]
      ; we use `original` file flag to distinguish between generated files
      ; (e.x. created those by plugins)
      (assoc parsed-yaml :original true)
      {:original true})
    {:original true}))

(defn remove-metadata [content]
  (let [splitted (str/split content #"---\n")]
    (if (> (count splitted) 2)
      (first (drop 2 splitted))
      content)))

(defn markdown-to-html [file-content]
  (-> file-content
      remove-metadata
      endophile/mp
      endophile/to-clj
      endophile/html-string))

(defn process-file [file]
  (let [file-content (slurp file)]
      (u/info "Processing Markdown: %s\n" (.getName file))
      [(.getName file) (merge (parse-file-metadata file-content)
                              {:content (markdown-to-html file-content)})]))

(defn parse-markdown [markdown-files]
  (let [parsed-files (into {} (map #(-> % io/file process-file) markdown-files))]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    parsed-files))
