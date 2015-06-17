(ns io.perun.markdown
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [endophile.core  :as endophile]
            [clj-yaml.core   :as yaml]))

(defn generate-filename
  "Default implementation for the `create-filename` task option"
  [file]
  (let [filepath (.getPath file)
        filename (last (clojure.string/split filepath #"/"))
        length (count filename)
        short-name (subs filename 9 (- length 3))]
        short-name))

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

; TODO we need to validate that create-filename is a function
(defn process-file [file options]
  (let [file-content (slurp file)
        metadata     (parse-file-metadata file-content)
        create-filename-fn (eval (read-string (:create-filename options)))
        filename     (create-filename-fn file)
        content      (markdown-to-html file-content)
        updated-meta (assoc metadata
                              :filename filename
                              :content content)]
      (u/info "Processing Markdown: %s\n" (.getName file))
      [(.getName file) updated-meta]))

(defn parse-markdown [markdown-files options]
  (let [parsed-files (into {} (map #(process-file (io/file %) options) markdown-files))]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    parsed-files))
