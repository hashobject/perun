(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.permalink
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]))

(def ^:private
  +defaults+ {:datafile "posts.edn"})

(boot/deftask permalink
  "Make files permalinked"
  [d datafile DATAFILE str "Datafile with all parsed meta information"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              posts (util/read-posts fileset (:datafile options))
              updated-posts
                (map
                  (fn [post]
                    (let [file-path (str (:target options) "/" (:filename post) "/index.html")]
                      (assoc post :filepath file-path)))
                  posts)]
          (util/save-posts tmp options updated-posts)
          (u/info "Added permalinks to %s files\n" (count updated-posts))
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))
