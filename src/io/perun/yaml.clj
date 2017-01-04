(ns io.perun.yaml
  (:require [clojure.string  :as str]
            [clj-yaml.core   :as yaml]
            [clojure.walk    :as walk])
  (:import [flatland.ordered.map OrderedMap]
           [flatland.ordered.set OrderedSet]))

(def ^:dynamic *yaml-head* #"---\r?\n")

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

(defn normal-colls
  "Clj-yaml keeps order of map properties by using ordered maps. These are inconvenient
  for us as the ordered library is not necessarily available in other pods."
  [x]
  (walk/postwalk
    (fn [y]
      (cond
        (instance? OrderedMap y) (into {} y)
        (instance? OrderedSet y) (into #{} y)
        :else y))
    x))

(defn parse-file-metadata [file-content]
  (when-let [metadata-str (substr-between file-content *yaml-head* *yaml-head*)]
    (normal-colls (yaml/parse-string metadata-str))))

(defn remove-metadata [content]
  (let [splitted (str/split content *yaml-head* 3)]
    (if (> (count splitted) 2)
      (first (drop 2 splitted))
      content)))
