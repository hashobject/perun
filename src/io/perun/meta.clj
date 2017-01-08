(ns io.perun.meta
  "Utilies for dealing with perun metadata"
  (:require [boot.core :as boot]))

(def +meta-key+ :io.perun)

(defn meta-from-file
  [tmpfile]
  (when-let [meta (+meta-key+ tmpfile)]
    (assoc meta :path (:path tmpfile))))

(defn get-meta
  "Return metadata on files. Files metadata is a list.
   Internally it's stored as a map indexed by `:path`"
  [fileset]
  (keep meta-from-file (vals (:tree fileset))))

(defn key-meta [data]
  (into {} (for [d data] [(:path d) d])))

(defn set-meta
  "Update `+meta-key+` metadata for files in `data` and return updated fileset"
  [fileset data]
  (boot/add-meta fileset (into {} (for [d data] [(:path d) {+meta-key+ (dissoc d :path)}]))))

(defn merge-meta* [m1 m2]
  (vals (merge-with merge (key-meta m1) (key-meta m2))))

(defn merge-meta [fileset data]
  (set-meta fileset (merge-meta* (get-meta fileset) data)))

(def +global-meta-key+ :io.perun.global)

(defn get-global-meta
  "Return global metadata that is related to the whole project
   and all files. Global metadata is a map"
  [fileset]
  (-> fileset meta +global-meta-key+))

(defn set-global-meta [fileset data]
  (vary-meta fileset assoc +global-meta-key+ data))
