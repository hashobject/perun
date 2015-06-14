(ns io.perun.rss
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [io.perun.date   :as date]
            [clojure.java.io :as io]
            [clj-rss.core    :as rss-gen]))

(defn rss-definitions [files]
  (for [file files]
    {:link (:canonical_url file)
     :guid (:canonical_url file)
     :pubDate (date/str-to-date (:date_published file))
     :title (:name file)
     :description (:description file)
     :author (:author_email file)}))

(defn generate-rss-str [files options]
  (let [opts (select-keys options [:title :description :link])
        items (rss-definitions files)
        rss-str (apply rss-gen/channel-xml opts items)]
    rss-str))

(defn generate-rss [tgt-path files-metadata options]
  (let [rss-filepath (str (:target options) "/" (:filename options))
        rss-string (generate-rss-str files-metadata options)]
    (perun/create-file tgt-path rss-filepath rss-string)
    (u/info (str "Generate RSS feed and save to " rss-filepath "\n"))))

