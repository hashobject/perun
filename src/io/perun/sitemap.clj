(ns io.perun.sitemap
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [sitemap.core    :as sitemap-gen]))

(defn create-sitemap [files options]
  (map
    (fn [file]
      {:loc (str (:url options) (:filename file))
       :lastmod (:date_modified file)
       :changefreq (or (:sitemap_changefreq file) "weekly")
       :priority (or (:sitemap_priority file) 0.8)})
    files))

(defn generate-sitemap [tgt-path datafile-path options]
  (let [sitemap-filepath (str (:target options) "/" (:filename options))
        files (perun/read-files-defs datafile-path)
        sitemap-xml (create-sitemap files options)
        sitemap-string (sitemap-gen/generate-sitemap sitemap-xml)]
    (perun/create-file tgt-path sitemap-filepath sitemap-string)
    (u/info (str "Generate sitemap and save to " sitemap-filepath "\n"))))

