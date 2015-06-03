(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [markdown-clj "0.9.40"]
                  [endophile "0.1.2"]])

(ns io.perun.markdown
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]
            [markdown.core   :as markdown-converter]
            [endophile.core  :as markdown-parser]))


(defn generate-filename [file]
  (let [filepath (:path file)
        filename (last (clojure.string/split filepath #"/"))
        length (count filename)
        short-name (subs filename 9 (- length 3))]
        short-name))

(def ^:private
  +defaults+ {:datafile "meta.edn"
              :create-filename generate-filename})

(defn file-to-clj [file]
  (-> file
      util/read-file
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
      util/read-file
      markdown-converter/md-to-html-string))

(defn process-file [file options]
  (if-let [file-def (file-to-clj file)]
    (if-let [data (:data file-def)]
      (let [lines (clojure.string/split data #"\n")
            create-filename-fn (:create-filename options)
            filename (create-filename-fn file)
            metadata (parse-file-defn lines)
            content (markdown-to-html file)]
        (assoc metadata :filename filename
                        :content content)))))

(boot/deftask markdown
  "Parse markdown files"
  [d datafile        DATAFILE        str "Target datafile with all parsed meta information"
   f create-filename CREATE_FILENAME code "Function that creates final target filename of the file"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              markdown-files (->> fileset boot/user-files (boot/by-ext [".md"]))
              parsed-files (map #(process-file % options) markdown-files)
              datafile (io/file tmp (:datafile options))
              content (prn-str parsed-files)]
          (util/write-to-file datafile content)
          (u/info "Parsed %s markdown files\n" (count markdown-files))
          (util/commit-and-next fileset tmp next-handler))))))
