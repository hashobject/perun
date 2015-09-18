(ns io.perun.markdown
  (:require [boot.util              :as u]
            [io.perun.core          :as perun]
            [clojure.java.io        :as io]
            [clojure.string         :as str]
            [endophile.core         :as endophile]
            [clj-yaml.core          :as yaml]
            [camel-snake-kebab.core :as kebab]
            [net.cgrand.enlive-html :as enlive]))

(defn substr-between
  "Find string that is nested in between two strings. Return first match.
  Copied from https://github.com/funcool/cuerdas"
  [s prefix suffix]
  (cond
    (nil? s) nil
    (nil? prefix) nil
    (nil? suffix) nil
    :else
    (some-> s
            (str/split prefix)
            second
            (str/split suffix)
            first)))

(defn parse-file-metadata [file-content]
  (if-let [metadata-str (substr-between file-content #"---\n" #"---\n")]
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


(defn id-from-node [h-node]
  (some-> h-node
          :content
          first
          kebab/->kebab-case))

(defn transform [enl]
  (let [new-enl
    (enlive/transform enl
      [#{:h1 :h2 :h3 :h4 :h5 :h6}]
      (fn [h-node]
        (let [id (id-from-node h-node)
              href (str "#" id)
              orig-header (first (:content h-node))
              new-content [{:tag :a, :attrs {:href href}, :content '({:tag :span})} orig-header]]
              (assoc h-node :content new-content))))]
  new-enl))

(defn markdown-to-html [file-content]
  (-> file-content
      remove-metadata
      endophile/mp
      endophile/to-clj
      transform
      endophile/html-string))

(defn process-file [file]
  (let [file-content (slurp file)]
    ; .getName returns only the filename so this should work cross platform
    (u/info "Processing Markdown: %s\n" (.getName file))
    [(.getName file) (merge (parse-file-metadata file-content)
                            {:content (markdown-to-html file-content)})]))

(defn parse-markdown [markdown-files]
  (let [parsed-files (into {} (map #(-> % io/file process-file) markdown-files))]
    (u/info "Parsed %s markdown files\n" (count markdown-files))
    parsed-files))
