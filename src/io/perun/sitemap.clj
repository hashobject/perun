(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [sitemap "0.2.4"]])

(ns io.perun.sitemap
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]
            [sitemap.core    :as sitemap-gen]))


(def ^:private
  +defaults+ {:filename "sitemap.xml"
              :target "public"
              :datafile "posts.edn"})

(defn posts-sitemap-definitions [posts options]
  (map
    (fn [post]
      {:loc (str (:url options) (:filename post))
       :lastmod (get post "date_modified")
       :changefreq "weekly"
       :priority 0.8})
    posts))


; TODO handle collections
(defn create-sitemap [posts options]
  (let [pages (posts-sitemap-definitions posts options)]
        pages))

(boot/deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str "Generated sitemap filename"
   o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   u url      URL      str "Base URL"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              posts (util/read-posts fileset (:datafile options))
              sitemap-filepath (str (:target options) "/" (:filename options))
              sitemap-file (io/file tmp sitemap-filepath)
              sitemap-xml (create-sitemap posts options)
              sitemap-string (sitemap-gen/generate-sitemap sitemap-xml)]
          (util/write-to-file sitemap-file sitemap-string)
          (u/info (str "Generate sitemap and save to " sitemap-filepath "\n"))
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))

