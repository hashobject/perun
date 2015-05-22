(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [time-to-read "0.1.0"]])

(ns io.perun.ttr
  {:boot/export-tasks true}
  (:require [boot.core         :as boot]
            [boot.util         :as u]
            [io.perun.utils :as util]
            [clojure.java.io   :as io]
            [time-to-read.core :as time-to-read]))

(boot/deftask ttr
  "Calculate time to read for each post"
  []
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [posts (util/read-posts fileset "posts.edn")
              updated-posts
                (map
                  (fn [post]
                    (let [time-to-read (time-to-read/estimate-for-text (:content post))]
                      (assoc post :ttr time-to-read)))
                  posts)
              posts-file (io/file tmp "posts.edn")
              content (prn-str updated-posts)]
          (util/write-to-file posts-file content)
          (u/info "Added TTR to %s posts\n" (count updated-posts))
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))
