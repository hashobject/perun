(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                 [clj-rss "0.1.9"]])

(ns io.perun.rss
  {:boot/export-tasks true}
  (:require [boot.core         :as boot]
            [boot.util         :as u]
            [io.perun.utils    :as util]
            [clojure.java.io   :as io]
            [clj-rss.core      :as rss-gen]))


(defn posts-rss-definitions [posts]
  (for [post posts]
    {:link (get post "canonical_url")
     :guid (get post "canonical_url")
     :pubDate (util/str-to-date (get post "date_published"))
     :title (get post "name")
     :description (get post "description")
     :author (get post "author_email")}))


(defn generate-rss-str [posts]
  (let [items (posts-rss-definitions posts)
        rss-str (apply rss-gen/channel-xml
          {:title "Hashobject team blog"
           ;:image "http://blog.hashobject.com/images/hashobject-logo.png"
           :link "http://blog.hashobject.com"
           :description "Hashobject - software engineering, design and application development"} items)]
    rss-str))

(boot/deftask rss
  "Generate RSS feed"
  []
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [posts (util/read-posts fileset "posts.edn")
              rss-file (io/file tmp "public/feed.rss")
              rss-string (generate-rss-str posts)]
          (util/write-to-file rss-file rss-string)
          (u/info "Generate RSS feed")
          (-> fileset
              (boot/add-resource tmp)
              boot/commit!
              next-handler))))))

