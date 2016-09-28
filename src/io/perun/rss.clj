(ns io.perun.rss
  (:require [io.perun.core :as perun]
            [clj-rss.core  :as rss-gen]))

(defn rss-definitions [files]
  (for [file files]
    {:link        (:canonical-url file)
     :guid        (:canonical-url file)
     :pubDate     (:date-published file)
     :title       (:title file)
     :description (:description file)
     :author      (:author-email file)}))

(defn generate-rss-str [files options]
  (let [rss-options  {:title       (or (:title options) (:site-title options))
                      :description (:description options)
                      :link        (or (:link options) (:base-url options))}
        items        (rss-definitions (filter :title files))
        rss-str      (apply rss-gen/channel-xml rss-options items)]
    rss-str))

(defn generate-rss [tgt-path files options]
  (let [rss-filepath (str (:out-dir options) "/" (:filename options))
        rss-string   (generate-rss-str files options)]
    (perun/create-file tgt-path rss-filepath rss-string)
    (perun/report-info "rss" "generated RSS feed and saved to %s" rss-filepath)))
