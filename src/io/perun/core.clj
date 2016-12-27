(ns io.perun.core
  "Utilies which can be used in base JVM and pods."
  (:require [clojure.java.io         :as io]
            [clojure.string          :as string]
            [boot.from.io.aviso.ansi :as ansi]
            [boot.util               :as u]))

(defn report-info [task msg & args]
  (apply u/info
        (str
          (ansi/yellow (str "[" task "]"))
          " - "
          (ansi/green (str msg "\n")))
        args))

(defn report-debug [task msg datastructure]
  (u/dbug
    (str
      (ansi/yellow (str "[" task "]"))
      " - "
      (ansi/blue (str msg "\n"))
      "%s\n")
    (pr-str datastructure)))


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
  "Removes slashes url start of the string."
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

(defn filename [name]
  (second (re-find #"(.+?)(\.[^.]*$|$)" (last (string/split name #"/")))))

(defn parent-path [filepath filename-with-extension]
  (if (.endsWith filepath filename-with-extension)
      (.substring filepath 0 (- (count filepath)
                              (count filename-with-extension)))
     filepath))

(defn ^String extension [name]
  (last (seq (string/split name #"\."))))

(defn assert-base-url [base-url]
  (assert (= \/ (last base-url))
          "base-url must end in \"/\"")
  base-url)
