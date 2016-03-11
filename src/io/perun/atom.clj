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

(defn generate-atom-str [posts {:keys [title description link filename]}]
  ; FIXME: title and link are required, Schema validation?
  (xml/emit-str
    (xml/sexp-as-element
      [:feed {:xmlns "http://www.w3.org/2005/Atom"}
       [:title title]
       (if description [:subtitle  description])
       [:link {:href (str link "/" filename) :rel "self"}]
       [:link {:href link}]
       [:updated (->> (take 10 posts)
                      (map updated)
                      (map iso-datetime)
                      sort
                      reverse
                      first)]
       [:id link]
       (for [{:keys [permalink canonical-url content name author author-email] :as post} (take 10 posts)]
         ; FIXME: permalink is required
         [:entry
          [:id permalink]
          [:title name]
          (if canonical-url [:link canonical-url])
          [:updated (iso-datetime (updated post))]
          [:content {:type "html"} (str content)]
          [:author
           [:name author]
           (if author-email [:email author-email])]
          ])])))

(defn generate-atom [tgt-path files options]
  (let [atom-filepath (str (:out-dir options) "/" (:filename options))
        atom-string   (generate-atom-str files options)]
    (perun/create-file tgt-path atom-filepath atom-string)
    (perun/report-info "atom" "generated Atom feed and saved to %s" atom-filepath)))
