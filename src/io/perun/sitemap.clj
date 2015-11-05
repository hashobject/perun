(ns io.perun.sitemap
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [sitemap.core    :as sitemap-gen]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]))

(defn iso-date [date]
  (tf/unparse (tf/formatters :year-month-day) (tc/from-date date)))

(defn last-modified [file]
  (iso-date
    (or (:date-modified file)
        (:date-build file))))

(defn create-sitemap [files]
  (map
    (fn [file]
      {:loc        (:canonical-url file)
       :lastmod    (last-modified file)
       :changefreq (or (:sitemap-changefreq file) "weekly")
       :priority   (or (:sitemap-priority file) 0.8)})
    files))

(defn generate-sitemap [tgt-path files options]
  (let [sitemap-filepath (str (:target options) "/" (:filename options))
        sitemap-xml      (create-sitemap files)
        sitemap-string   (sitemap-gen/generate-sitemap sitemap-xml)]
    (perun/create-file tgt-path sitemap-filepath sitemap-string)
    (u/info "Generated sitemap and saved to %s\n" sitemap-filepath)))
