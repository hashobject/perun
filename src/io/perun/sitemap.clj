(ns io.perun.sitemap
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [sitemap.core    :as sitemap-gen]))

(defn create-sitemap [files]
  (map
    (fn [file]
      {:loc        (:canonical-url file)
       :lastmod    (or (:date-modified file)
                       (:date-build file))
       :changefreq (or (:sitemap-changefreq file) "weekly")
       :priority   (or (:sitemap-priority file) 0.8)})
    files))

(defn generate-sitemap [tgt-path files options]
  (let [sitemap-filepath (str (:target options) "/" (:filename options))
        sitemap-xml      (create-sitemap files)
        sitemap-string   (sitemap-gen/generate-sitemap sitemap-xml)]
    (perun/create-file tgt-path sitemap-filepath sitemap-string)
    (u/info "Generate sitemap and save to %s\n" sitemap-filepath)))
