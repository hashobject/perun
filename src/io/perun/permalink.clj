(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.permalink
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]))

(def ^:private
  +defaults+ {:datafile "meta.edn"})

(defn create-filepath [file options]
  (let [file-path (str (:target options) "/" (:filename file) "/index.html")]
    (assoc file :filepath file-path)))

(boot/deftask permalink
  "Make files permalinked"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (util/read-files-defs fileset (:datafile options))
              updated-files (map #(create-filepath % options) files)]
          (util/save-files-defs tmp options updated-files)
          (u/info "Added permalinks to %s files\n" (count updated-files))
          (util/commit-and-next fileset tmp next-handler))))))
