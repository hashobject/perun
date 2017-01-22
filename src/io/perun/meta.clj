(ns io.perun.meta
  "Utilies for dealing with perun metadata"
  (:require [boot.core :as boot]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [io.perun.core :as perun]))

(def +meta-key+ :io.perun)

(def +global-meta-key+ :io.perun.global)

(def +global-meta-defaults+
  {:doc-root "public"})

(defn get-global-meta
  "Return global metadata that is related to the whole project
   and all files. Global metadata is a map"
  [fileset]
  (merge +global-meta-defaults+ (-> fileset meta +global-meta-key+)))

(defn set-global-meta [fileset data]
  (vary-meta fileset assoc +global-meta-key+ data))

(defn slug [name]
  (second (re-find #"(.+?)(\.[^.]*$|$)" name)))

(defn path-meta
  [path {:keys [doc-root base-url]} & [file]]
  (let [file (or file (io/file path))
        filename (.getName file)
        slug (slug filename)
        match-doc-root (re-pattern (str "^" doc-root))
        permalink (-> path
                      (string/replace match-doc-root "")
                      (string/replace #"(^|/)index\.html$" "/")
                      perun/absolutize-url)]
    (merge {:path path
            :parent-path (perun/parent-path path filename)
            :full-path (.getPath file)
            :permalink permalink
            :filename filename
            :slug slug
            :short-filename slug ;; for backwards compatibility
            :extension (perun/extension filename)}
           (when base-url
             (perun/assert-base-url base-url)
             {:canonical-url (str base-url (subs permalink 1))}))))

(defn meta-from-file
  [fileset tmpfile]
  (let [path (boot/tmp-path tmpfile)
        file (boot/tmp-file tmpfile)
        perun-meta (+meta-key+ tmpfile)
        global-meta (get-global-meta fileset)]
    (merge perun-meta (path-meta path global-meta file))))

(defn get-meta
  "Return metadata on files. Files metadata is a list.
   Internally it's stored as a map indexed by `:path`"
  [fileset]
  (map (partial meta-from-file fileset) (boot/ls fileset)))

(defn key-meta [data]
  (into {} (for [d data] [(:path d) d])))

(def derived-meta-keys
  [:path :parent-path :full-path :permalink :filename :slug :short-filename
   :extension :canonical-url :content])

(defn set-meta
  "Update `+meta-key+` metadata for files in `data` and return updated fileset"
  [fileset data]
  (->> (for [d data] [(:path d) {+meta-key+ (apply dissoc d derived-meta-keys)}])
       (into {})
       (boot/add-meta fileset)))

(defn merge-meta [m1 m2]
  (vals (merge-with merge (key-meta m1) (key-meta m2))))
