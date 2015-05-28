(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]])

(ns io.perun.render
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]))


(def ^:private
  +defaults+ {:target "public"
              :datafile "posts.edn"})

(boot/deftask render
  "Render pages/posts"
  [o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   r renderer RENDERER sym "Page renderer"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              posts (util/read-posts fileset (:datafile options))]
           (doall
            (map
              (fn [post]
                (let [render-fn (resolve renderer)
                      html (render-fn post)
                      post-file-path (str (:target options) "/"
                                          (or (:filepath post)
                                              (str (:filename post) ".html")))
                      post-file (io/file tmp post-file-path)]
                  (util/write-to-file post-file html)))
            posts))
          (u/info (str "Render all pages/posts\n"))
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))

