(ns io.perun.sitemap
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [sitemap.core    :as sitemap-gen]))

(defn create-sitemap [files options]
  (map
    (fn [file]
      {:loc (str (:url options) (:filename file))
       :lastmod (or (:date-modified file)
                    (:build-date file))
       :changefreq (or (:sitemap-changefreq file) "weekly")
       :priority (or (:sitemap-priority file) 0.8)})
    files))

(defn generate-sitemap [tgt-path files-metadata options]
  (let [sitemap-filepath (str (:target options) "/" (:filename options))
        sitemap-xml      (create-sitemap files-metadata options)
        sitemap-string   (sitemap-gen/generate-sitemap sitemap-xml)]
    (perun/create-file tgt-path sitemap-filepath sitemap-string)
    (u/info "Generate sitemap and save to %s\n" sitemap-filepath)))
