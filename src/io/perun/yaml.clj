(ns io.perun.yaml
  (:require [clj-yaml.core   :as yaml]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [clojure.walk    :as walk]
            [io.perun.core   :as perun])
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

(defn remove-metadata [content]
  (let [splitted (str/split content *yaml-head* 3)]
    (if (> (count splitted) 2)
      (first (drop 2 splitted))
      content)))

(defn parse-yaml [{:keys [entry]} keep-yaml]
  (let [content (-> entry :full-path io/file slurp)
        parsed-metadata (if-let [metadata-str (substr-between content *yaml-head* *yaml-head*)]
                          (normal-colls (yaml/parse-string metadata-str))
                          {})
        rendered (if keep-yaml
                   content
                   (remove-metadata content))]
    (merge entry parsed-metadata {:rendered rendered})))
