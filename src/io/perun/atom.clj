(ns io.perun.atom
  (:require [boot.util        :as u]
            [io.perun.core    :as perun]
            [clojure.data.xml :as xml]
            [clj-time.core    :as t]
            [clj-time.coerce  :as tc]
            [clj-time.format  :as tf]))

;; Check https://github.com/jekyll/jekyll-feed/blob/master/lib/jekyll-feed/feed.xml for tags to use

(defn published [{:keys [date-published date-created]}]
  (or date-published date-created))

(defn updated [{:keys [date-modified] :as post}]
  (or date-modified (published post)))

(defn iso-datetime [date]
  (tf/unparse (tf/formatters :date-time-no-ms) (tc/from-date date)))

(defn nav-hrefs
  [{:keys [next-page prev-page first-page last-page out-dir doc-root base-url]}]
  (->> [next-page prev-page first-page last-page]
       (map #(when %
               (perun/path->canonical-url
                (perun/create-filepath out-dir %)
                doc-root
                base-url)))
       (map vector [:next :prev :first :last])
       (into {})))

(defn generate-atom [{:keys [entry entries meta]}]
  (let [{:keys [site-title description base-url canonical-url] :as options} (merge meta entry)
        {global-author :author global-author-email :author-email} meta
        navs (nav-hrefs options)
        atom (xml/emit-str
              (xml/sexp-as-element
               [:feed {:xmlns "http://www.w3.org/2005/Atom"}
                [:title site-title]
                (when (seq description)
                  [:subtitle description])
                ;; FIXME: :version property
                [:generator {:uri "https://perun.io/"} "Perun"]
                [:link {:href base-url :type "text/html"}]
                [:link {:href canonical-url :rel "self"}]
                [:link {:href (:first navs) :rel "first"}]
                [:link {:href (:last navs) :rel "last"}]
                (when-let [next (:next navs)]
                  [:link {:href next :rel "next"}])
                (when-let [prev (:prev navs)]
                  [:link {:href prev :rel "previous"}])
                [:updated (->> entries
                               (map (comp iso-datetime updated first))
                               sort
                               reverse
                               first)]
                [:id base-url]

                (when global-author
                  [:author
                   [:name global-author]
                   (when global-author-email
                     [:email global-author-email])])

                (for [{:keys [uuid canonical-url title author
                              author-email category tags content] :as post} entries
                      :let [author (or author global-author)
                            author-email (or author-email global-author-email)]]
                  [:entry
                   [:id (str "urn:uuid:" uuid)]
                   [:title title]
                   (when canonical-url
                     [:link {:href canonical-url :type "text/html" :title title :rel "alternate"}])
                   [:published (iso-datetime (published post))]
                   [:updated (iso-datetime (updated post))]
                   [:content {:type "html" :xml:base canonical-url} (str content)]
                   [:author
                    [:name author]
                    (when author-email [:email author-email])]
                   (for [tag tags]
                     [:category {:term tag}])
                   ;; FIXME: post-image media:thumbnail
                   ])]))]
    (assoc entry :rendered atom)))
