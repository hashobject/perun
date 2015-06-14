(ns io.perun.markdown
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [markdown.core   :as markdown-converter]
            [endophile.core  :as markdown-parser]))


(defn generate-filename
  "Default implementation for the `create-filename` task option"
  [file]
  (let [filepath (.getPath file)
        filename (last (clojure.string/split filepath #"/"))
        length (count filename)
        short-name (subs filename 9 (- length 3))]
        short-name))


(defn file-to-clj [file]
  (-> file
      slurp
      markdown-parser/mp
      markdown-parser/to-clj
      first))

(defn trim-if-not-nil [s]
  (if (clojure.string/blank? s)
    s
    (clojure.string/trim s)))


(defn parse-file-defn [lines]
  ; we use `original` file flag to distinguish between generated files
  ; (e.x. created those by plugins)
  (let [metadata {:original true}]
        (into metadata
          (for [line lines]
            (let [tokens (clojure.string/split line #":" 2)
                  key-token (trim-if-not-nil (first tokens))
                  value-token (trim-if-not-nil (second tokens))]
                  (if (not (clojure.string/blank? key-token))
                    [(keyword key-token) value-token]))))))


(defn markdown-to-html [file]
  (-> file
      slurp
      markdown-converter/md-to-html-string))

; TODO we need to validate that create-filename is a function
(defn process-file [file options]
  (if-let [file-def (file-to-clj file)]
    (if-let [data (:data file-def)]
      (let [lines (clojure.string/split data #"\n")
            create-filename-fn (eval (read-string (:create-filename options)))
            filename (create-filename-fn file)
            metadata (parse-file-defn lines)
            content (markdown-to-html file)]
        (assoc metadata :filename filename
                        :content content)))))

(defn parse-markdown [tgt-path options markdown-files]
  (let [parsed-files (map #(process-file (io/file %) options) markdown-files)]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    parsed-files))
