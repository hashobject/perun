(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                 [sitemap "0.2.4"]])

(ns io.perun.sitemap
  {:boot/export-tasks true}
  (:require [boot.core         :as boot]
            [boot.util         :as u]
            [io.perun.utils :as util]
            [clojure.java.io   :as io]
            [sitemap.core      :as sitemap-gen]))


(defn posts-sitemap-definitions [posts]
  (map
    (fn [post]
      {:loc (str "http://blog.hashobject.com/" (:filename post))
       :lastmod (get post "date_modified")
       :changefreq "weekly"
       :priority 0.8}) posts))


; TODO fix changefreq for index page
; DEFAULT LOC should be from opts
(defn create-sitemap [posts]
  (let [posts-pages (posts-sitemap-definitions posts)
        all-pages (conj posts-pages
                        {:loc (str "http://blog.hashobject.com/")
                         :lastmod "2013-06-26"
                         :changefreq "daily"
                         :priority 1.0})]
        all-pages))

(boot/deftask sitemap
  "Generate sitemap"
  []
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [posts (util/read-posts "posts.edn")
              sitemap-file (io/file tmp "public/sitemap.xml")
              sitemap-xml (create-sitemap posts)
              sitemap-string (sitemap-gen/generate-sitemap sitemap-xml)]
          (util/write-to-file sitemap-file sitemap-string)
          (u/info "Generate sitemap")
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))

