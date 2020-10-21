(ns io.perun.rss
  (:require [io.perun.core :as perun]
            [clj-rss.core  :as rss-gen]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]))

(defn iso-datetime [date]
  (if date
    (tf/unparse (tf/formatters :date-time-no-ms) (tc/from-date date))
    :default))

(defn rss-definitions [files]
  (reverse
   ;; INFO: Whilst the `:date-published` is in RFC 822 format (i.e.
   ;; Sat, 10 Oct 2020 02:00:00 +0200), having a 'correct' sort is
   ;; relevant. Otherwise feed readers interestingly will display an
   ;; ordering by the names of week days.
   (sort-by #(iso-datetime (:pubDate %))
            (for [file files]
              {:link        (:canonical-url file)
               :guid        (:canonical-url file)
               :pubDate     (:date-published file)
               :title       (:title file)
               ;; FIXME: Why is there no `:content` attribute
               ;; available? In the `:content`, there would be the
               ;; whole post. The `atom` task has `:content`
               ;; available.
               ;; :description (str
               ;;               "<![CDATA["
               ;;               (:content file)
               ;;               "]]>")
               :description (str
                             "<![CDATA["
                             "Description: "
                             (:description file)
                             "<br><br>"
                             "Read the full article here: <a href=\""
                             (:canonical-url file)
                             "\">"
                             (:canonical-url file)
                             "</a>"
                             "]]>")
               ;; INFO: The RSS spec says it should
               ;; be one email, and does not say how to handle multiple authors.
               ;; have multiple authors.
               ;; INFO: The specified way would be to
               ;; include a "dc:creator" attribute
               ;; per author without email, however
               ;; clj-rss does not allow for that:
               ;; https://github.com/yogthos/clj-rss/blob/1399a134d48f9a699e49edd9bc1d9250301a37fd/src/clj_rss/core.clj#L74.
               ;; For this reason, we're going to include one email
               ;; address for potentially multiple authors.
               ;; TODO: Join it here.
               :author      (let [author (:author file)
                                  author-email (:author-email file)]
                              (if (and author author-email)
                                (str (:author-email file) " (" author ")")
                                author-email))}))))

(defn generate-rss-str [files options]
  (let [rss-options  {:title       (:site-title options)
                      :description (:description options)
                      :link        (:base-url options)}
        items        (rss-definitions (filter :title files))
        rss-str      (apply rss-gen/channel-xml rss-options items)]
    rss-str))

(defn generate-rss [tgt-path files options]
  (let [rss-filepath (str (:out-dir options) "/" (:filename options))
        rss-string   (generate-rss-str files options)]
    (perun/create-file tgt-path rss-filepath rss-string)
    (perun/report-info "rss" "generated RSS feed and saved to %s" rss-filepath)))
