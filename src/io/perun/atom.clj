(ns io.perun.atom
  (:require [boot.util        :as u]
            [io.perun.core    :as perun]
            [clojure.data.xml :as xml]
            [clj-time.core    :as t]
            [clj-time.coerce  :as tc]
            [clj-time.format  :as tf]))

(defn updated [{:keys [date-modified date-published date-created]}]
  (or date-modified date-published date-created))

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
       [:link {:href (str base-url filename) :rel "self"}]
       [:link {:href base-url}]
       [:updated (->> (take 10 posts)
                      (map updated)
                      (map iso-datetime)
                      sort
                      reverse
                      first)]
       [:id base-url]
       (for [{:keys [uuid canonical-url content name author author-email] :as post} (take 10 posts)
             :let [author (or author (:name (:author global-metadata)))
                   author-email (or author (:email (:author global-metadata)))]]
         (do
           (assert (seq uuid) (format "Atom XML requires that each post has a unique uuid, if you need one, use this: %s" (str (java.util.UUID/randomUUID))))
           (assert (seq author) (format "Atom XML requires that each post has author name."))
           [:entry
            [:id (str "urn:uuid:" uuid)]
            [:title name]
            (if canonical-url
              [:link {:href canonical-url}])
            [:updated (iso-datetime (updated post))]
            [:content {:type "html"} (str content)]
            [:author
             [:name author]
             (if author-email [:email author-email])]
            ]))])))

(defn generate-atom [tgt-path files options]
  (let [atom-filepath (str (:out-dir options) "/" (:filename options))
        atom-string   (generate-atom-str files options)]
    (perun/create-file tgt-path atom-filepath atom-string)
    (perun/report-info "atom" "generated Atom feed and saved to %s" atom-filepath)))
