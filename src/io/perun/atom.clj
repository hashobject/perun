(ns io.perun.atom
  (:require [boot.util        :as u]
            [io.perun.core    :as perun]
            [clojure.java.io  :as io]
            [clojure.data.xml :as xml]
            [clj-time.core    :as t]
            [clj-time.coerce  :as tc]
            [clj-time.format  :as tf]))

(defn updated [{:keys [date-modified date-published date-created]}]
  (or date-modified date-published date-created))

(defn iso-datetime [date]
  (tf/unparse (tf/formatters :date-time-no-ms) (tc/from-date date)))


(defn generate-atom-str [posts options]
  (let [opts (select-keys options [:title :subtitle :link])
        base-url (:link opts)]
    (xml/emit-str
      (xml/sexp-as-element
        [:feed {:xmlns "http://www.w3.org/2005/Atom"}
         [:title (:title opts)]
         [:subtitle (:subtitle opts)]
         [:link {:href (str base-url "/atom.xml") :rel "self"}]
         [:link {:href base-url}]
         [:updated (->> (take 10 posts)
                        (map updated)
                        (map iso-datetime)
                        sort
                        reverse
                        first)]
         [:id base-url]
         (for [{:keys [permalink canonical-url content name] :as post} (take 10 posts)]
           [:entry
            [:id permalink]
            [:title name]
            [:link canonical-url]
            [:updated (iso-datetime (updated post))]
            [:content {:type "html"} (str content)]
            [:author
              [:name (:author post)]
              [:email (:author-email post)]]
            ])]))))


(defn generate-atom [tgt-path files options]
  (let [atom-filepath (str (:target options) "/" (:filename options))
        atom-string   (generate-atom-str files options)]
    (perun/create-file tgt-path atom-filepath atom-string)
    (u/info "Generate Atom feed and save to %s\n" atom-filepath)))

