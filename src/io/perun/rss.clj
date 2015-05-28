(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [clj-rss "0.1.9"]])

(ns io.perun.rss
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]
            [clj-rss.core    :as rss-gen]))

(def ^:private
  +defaults+ {:filename "feed.rss"
              :target "public"
              :datafile "posts.edn"})

(defn posts-rss-definitions [posts]
  (for [post posts]
    {:link (get post "canonical_url")
     :guid (get post "canonical_url")
     :pubDate (util/str-to-date (get post "date_published"))
     :title (get post "name")
     :description (get post "description")
     :author (get post "author_email")}))

(defn generate-rss-str [posts options]
  (let [opts (select-keys options [:title :description :link])
        items (posts-rss-definitions posts)
        rss-str (apply rss-gen/channel-xml opts items)]
    rss-str))

(boot/deftask rss
  "Generate RSS feed"
  [f filename    FILENAME    str "Generated RSS feed filename"
   o target      OUTDIR      str "The output directory"
   d datafile    DATAFILE    str "Datafile with all parsed meta information"
   t title       TITLE       str "RSS feed title"
   p description DESCRIPTION str "RSS feed description"
   l link        LINK        str "RSS feed link"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              posts (util/read-posts fileset (:datafile options))
              rss-filepath (str (:target options) "/" (:filename options))
              rss-string (generate-rss-str posts options)]
          (util/create-file tmp rss-filepath rss-string)
          (u/info (str "Generate RSS feed and save to " rss-filepath "\n"))
          (util/commit-and-next fileset tmp next-handler))))))

