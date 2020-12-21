(ns io.perun.core
  "Utilies which can be used in base JVM and pods."
  (:require [clojure.java.io         :as io]
            [clojure.string          :as string]
            [boot.from.io.aviso.ansi :as ansi]
            [boot.util               :as u]))

(def ^:private +version-file-name+ "version.properties")

(defn perun-version
  []
  (let [m (doto (java.util.Properties.)
            (.load ^java.io.Reader (io/reader +version-file-name+)))]
    (if-let [[k v] (find m "VERSION")]
      v
      (throw (Exception. (str "The " +version-file-name+ " file does not contain a VERSION property, this is a bug."))))))

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
  (string/replace url #"^/*" ""))

(defn create-filepath
  "Creates a filepath using system path separator."
  [& args]
  (.getPath (apply io/file (remove empty? args))))

(defn url-to-path
  "Converts a url to filepath."
  [url]
  (apply create-filepath (string/split url #"/")))

(def file-separator (re-pattern (java.util.regex.Pattern/quote java.io.File/separator)))

(defn path-to-url
  "Converts a path to url"
  [path]
  (->> file-separator
       re-pattern
       (string/split path)
       (string/join "/")))

(defn parent-path [filepath filename-with-extension]
  (if (.endsWith filepath filename-with-extension)
    (.substring filepath 0 (- (count filepath)
                              (count filename-with-extension)))
    filepath))

(defn filename [name]
  (second (re-find #"(.+?)(\.[^.]*$|$)" (last (string/split name (re-pattern file-separator))))))

(defn ^String extension [name]
  (last (seq (string/split name #"\."))))

(defn assert-base-url [base-url]
  (assert (= \/ (last base-url))
          "base-url must end in \"/\"")
  base-url)

(defn path->permalink
  [path doc-root]
  (let [match-doc-root (if (= doc-root ".")
                         ""
                         (re-pattern (str "^" doc-root)))]
    (-> path
        (string/replace match-doc-root "")
        path-to-url
        (string/replace #"(^|/)index\.html$" "/")
        absolutize-url)))

(defn permalink->canonical-url
  [permalink base-url]
  (str base-url (subs permalink 1)))

(defn path->canonical-url
  [path doc-root base-url]
  (let [permalink (path->permalink path doc-root)]
    (permalink->canonical-url permalink base-url)))
