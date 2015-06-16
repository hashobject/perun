(ns io.perun.markdown
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [markdown.core   :as markdown-converter]))


(defn generate-filename
  "Default implementation for the `create-filename` task option"
  [file]
  (let [filepath (.getPath file)
        filename (last (clojure.string/split filepath #"/"))
        length (count filename)
        short-name (subs filename 9 (- length 3))]
        short-name))

(defn trim-if-not-nil [s]
  (if (clojure.string/blank? s)
    s
    (clojure.string/trim s)))


(defn extract-between [s prefix suffix]
  (-> s
      (clojure.string/split prefix)
      second
      (clojure.string/split suffix)
      first))


(defn parse-file-metadata [content]
  (let [metadata-str (extract-between content #"---" #"---")
        metadata-lines (clojure.string/split metadata-str #"\n")
        ; we use `original` file flag to distinguish between generated files
        ; (e.x. created those by plugins)
        metadata {:original true}]
    (into metadata
      (for [line metadata-lines]
        (let [tokens (clojure.string/split line #":" 2)
              key-token (trim-if-not-nil (first tokens))
              value-token (trim-if-not-nil (second tokens))]
              (if (not (clojure.string/blank? key-token))
                [(keyword key-token) value-token]))))))

(defn file-to-metadata [file]
  (-> file
      slurp
      parse-file-metadata))

(defn remove-metadata [content]
  (first (drop 2 (clojure.string/split content #"---"))))

(defn markdown-to-html [file]
  (-> file
      slurp
      remove-metadata
      markdown-converter/md-to-html-string))

; TODO we need to validate that create-filename is a function
(defn process-file [file options]
  (if-let [metadata (file-to-metadata file)]
    (let [create-filename-fn (eval (read-string (:create-filename options)))
          filename (create-filename-fn file)
          content (markdown-to-html file)]
        (assoc metadata :filename filename
                        :content content))))

(defn parse-markdown [markdown-files options]
  (let [parsed-files (map #(process-file (io/file %) options) markdown-files)
        files (remove nil? parsed-files)]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    files))
