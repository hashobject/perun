(ns io.perun.meta
  "Utilies for dealing with perun metadata"
  (:require [boot.core :as boot]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [io.perun.core :as perun]))

(def +meta-key+ :io.perun)

(defn slug [name]
  (second (re-find #"(.+?)(\.[^.]*$|$)" name)))

(defn path-meta
  [path out-dir & [file]]
  (let [file (or file (io/file path))
        filename (.getName file)
        slug (slug filename)
        match-out-dir (re-pattern (str "^" out-dir))
        permalink (-> path
                      (string/replace match-out-dir "")
                      (string/replace #"(^|/)index\.html$" "/")
                      perun/absolutize-url)]
    {:path path
     :parent-path (perun/parent-path path filename)
     :full-path (.getPath file)
     :permalink permalink
     :filename filename
     :slug slug
     :short-filename slug ;; for backwards compatibility
     :extension (perun/extension filename)}))

(defn meta-from-file
  [tmpfile]
  (let [path (boot/tmp-path tmpfile)
        file (boot/tmp-file tmpfile)
        perun-meta (+meta-key+ tmpfile)]
    (merge perun-meta (path-meta path (:out-dir perun-meta) file))))

(defn get-meta
  "Return metadata on files. Files metadata is a list.
   Internally it's stored as a map indexed by `:path`"
  [fileset]
  (map meta-from-file (vals (:tree fileset))))

(defn key-meta [data]
  (into {} (for [d data] [(:path d) d])))

(defn set-meta
  "Update `+meta-key+` metadata for files in `data` and return updated fileset"
  [fileset data]
  (boot/add-meta fileset
                 (into {} (for [d data]
                            [(:path d)
                             {+meta-key+
                              (dissoc d
                                      :path :parent-path :full-path :permalink
                                      :filename :slug :extension :content)}]))))

(def +global-meta-key+ :io.perun.global)

(defn get-global-meta
  "Return global metadata that is related to the whole project
   and all files. Global metadata is a map"
  [fileset]
  (-> fileset meta +global-meta-key+))

(defn set-global-meta [fileset data]
  (vary-meta fileset assoc +global-meta-key+ data))
