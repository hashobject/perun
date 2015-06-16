(ns io.perun.markdown
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [markdown.core   :as markdown-converter]
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
  (-> s
      (clojure.string/split prefix)
      second
      (clojure.string/split suffix)
      first))

(defn parse-file-metadata [file-content]
  (let [metadata-str (extract-between file-content #"---\n" #"---\n")
        parsed-yaml (yaml/parse-string metadata-str)]
    ; we use `original` file flag to distinguish between generated files
    ; (e.x. created those by plugins)
    (assoc parsed-yaml :original true)))

(defn remove-metadata [content]
  (first (drop 2 (str/split content #"---\n"))))

(defn markdown-to-html [file-content]
  (-> file-content
      remove-metadata
      markdown-converter/md-to-html-string))

; TODO we need to validate that create-filename is a function
(defn process-file [file options]
  (let [file-content (slurp file)]
    (if-let [metadata (parse-file-metadata file-content)]
      (let [create-filename-fn (eval (read-string (:create-filename options)))
            filename (create-filename-fn file)
            content (markdown-to-html file-content)]
          (assoc metadata :filename filename
                          :content content)))))

(defn parse-markdown [markdown-files options]
  (let [parsed-files (map #(process-file (io/file %) options) markdown-files)
        files (remove nil? parsed-files)]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    files))
