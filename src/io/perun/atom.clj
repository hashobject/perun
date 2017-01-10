(ns io.perun.atom
  (:require [boot.util        :as u]
            [io.perun.core    :as perun]
            [clojure.data.xml :as xml]
            [clj-time.core    :as t]
            [clj-time.coerce  :as tc]
            [clj-time.format  :as tf]))

;; Check https://github.com/jekyll/jekyll-feed/blob/master/lib/feed.xml for tags to use

(defn published [{:keys [date-published date-created]}]
  (or date-published date-created))

(defn updated [{:keys [date-modified] :as post}]
  (or date-modified (published post)))

(defn iso-datetime [date]
  (tf/unparse (tf/formatters :date-time-no-ms) (tc/from-date date)))

(defn generate-atom-str [posts {:keys [site-title description base-url filename] :as global-metadata}]
  (assert (seq site-title) "Atom XML requires non-empty site-title")
  (assert (seq base-url) "Atom XML requires full base-url")
  ;; FIXME: chould validate that it is an URL?
  (xml/emit-str
    (xml/sexp-as-element
      [:feed {:xmlns "http://www.w3.org/2005/Atom"}
       [:title site-title]
       (if (seq description)
         [:subtitle description])
       ;; FIXME: :version property
       [:generator {:uri "https://perun.io/"} "Perun"]
       [:link {:href (str base-url filename) :rel "self"}]
       [:link {:href base-url :type "text/html"}]
       [:updated (->> (take 10 posts)
                      (map (comp iso-datetime updated first))
                      sort
                      reverse
                      first)]
       [:id base-url]

       (if (:author global-metadata)
         [:author
          [:name (:author global-metadata)]
          [:email (:author-email global-metadata)]])

       (for [[{:keys [uuid canonical-url title author author-email] :as post} content] (take 10 posts)
             :let [author (or author (:author global-metadata))
                   author-email (or author-email (:author-email global-metadata))]]
         (do
           (assert (seq uuid) (format "Atom XML requires that each post has a unique uuid, if you need one, use this: %s. Post %s is missing one" (str (java.util.UUID/randomUUID)) canonical-url))
           (assert (seq author) (format "Atom XML requires that each post has author name. Post %s is missing one" canonical-url))
           [:entry
            [:id (str "urn:uuid:" uuid)]
            [:title title]
            (if canonical-url
              [:link {:href canonical-url :type "text/html" :title title}])
            [:published (iso-datetime (published post))]
            [:updated (iso-datetime (updated post))]
            ;; FIXME: plain text on xml:base property
            [:content {:type "html"} (str content)]
            [:author
             [:name author]
             (if author-email [:email author-email])]
            ;; FIXME: category & tags [:category {:term "tag"}]
            ;; FIXME: post-image media:thumbnail
            ]))])))

(defn generate-atom [tgt-path files options]
  (let [atom-filepath (str (:out-dir options) "/" (:filename options))
        atom-string   (generate-atom-str files options)]
    (perun/create-file tgt-path atom-filepath atom-string)
    (perun/report-info "atom" "generated Atom feed and saved to %s" atom-filepath)))
