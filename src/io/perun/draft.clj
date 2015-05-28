(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [time-to-read "0.1.0"]])

(ns io.perun.draft
  {:boot/export-tasks true}
  (:require [boot.core         :as boot]
            [boot.util         :as u]
            [io.perun.utils    :as util]
            [clojure.java.io   :as io]))

(def ^:private
  +defaults+ {:datafile "posts.edn"})

(boot/deftask draft
  "Exclude draft posts"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              posts (util/read-posts fileset (:datafile options))
              updated-posts (remove #(true? (:draft %)) posts)
              posts-file (io/file tmp (:datafile options))
              content (prn-str updated-posts)]
          (util/write-to-file posts-file content)
          (u/info "Remove draft posts. Remaining %s posts\n" (count updated-posts))
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))
