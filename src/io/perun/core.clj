(ns io.perun.core
  "Utilies which can be used in base JVM and pods."
  (:require [clojure.java.io :as io]
            [clojure.string  :as string]))

(def +meta-key+ :io.perun)

(defn get-meta [fileset]
  (-> fileset meta +meta-key+ vals))

(defn key-meta [data]
  (into {} (for [d data] [(:path d) d])))

(defn set-meta [fileset data]
  (vary-meta fileset assoc +meta-key+ (key-meta data)))

(defn merge-meta* [m1 m2]
  (vals (merge-with merge (key-meta m1) (key-meta m2))))

(defn merge-meta [fileset data]
  (set-meta fileset (merge-meta* (get-meta fileset) data)))

(def +global-meta-key+ :io.perun.global)

(defn get-global-meta [fileset]
  (-> fileset meta +global-meta-key+))

(defn set-global-meta [fileset data]
  (vary-meta fileset assoc +global-meta-key+ data))

(defn write-to-file [out-file content]
  (doto out-file
    io/make-parents
    (spit content)))

(defn create-file [tmp filepath content]
  (let [file (io/file tmp filepath)]
    (write-to-file file content)))

(defn absolutize-url
  "Makes sure the url starts with slash."
  [url]
  (if (.startsWith url "/")
    url
    (str "/" url)))

(defn relativize-url
  "Remodes slashes url start of the string."
  [url]
  (string/replace url #"^[\/]*" ""))

(defn create-filepath
  "Creates a filepath using system path separator."
  [& args]
  (.getPath (apply io/file args)))

(defn url-to-path
  "Converts a url to filepath."
  [url]
  (apply create-filepath (string/split (relativize-url url) #"\/")))
