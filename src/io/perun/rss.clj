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
              :datafile "meta.edn"})

(defn rss-definitions [files]
  (for [file files]
    {:link (get file "canonical_url")
     :guid (get file "canonical_url")
     :pubDate (util/str-to-date (get file "date_published"))
     :title (get file "name")
     :description (get file "description")
     :author (get file "author_email")}))

(defn generate-rss-str [files options]
  (let [opts (select-keys options [:title :description :link])
        items (rss-definitions files)
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
              files (util/read-files-defs fileset (:datafile options))
              rss-filepath (str (:target options) "/" (:filename options))
              rss-string (generate-rss-str files options)]
          (util/create-file tmp rss-filepath rss-string)
          (u/info (str "Generate RSS feed and save to " rss-filepath "\n"))
          (util/commit-and-next fileset tmp next-handler))))))

