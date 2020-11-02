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

;; Declare a URI alias in this namespace
;; Rationale: Remove the auto-generated namespace in the feed, because that will lead to invalid feeds. More documentation here:
;;  - https://github.com/hashobject/perun/pull/251
;;  - https://stackoverflow.com/questions/64611482/how-to-generate-a-xml-without-a-namespace-with-the-latest-version-of-org-clojure
(xml/alias-uri 'atom "http://www.w3.org/2005/Atom")

(defn generate-atom [{:keys [entry entries meta]}]
  (let [{:keys [site-title description base-url
                canonical-url io.perun/version] :as options} (merge meta entry)
        {global-author :author global-author-email :author-email} meta
        navs (nav-hrefs options)
        atom (xml/emit-str
              (xml/sexp-as-element
               [::atom/feed {:xmlns "http://www.w3.org/2005/Atom"}
                [::atom/title site-title]
                (when (seq description)
                  [::atom/subtitle description])
                [::atom/generator {:uri "https://perun.io/" :version version} "Perun"]
                [::atom/link {:href base-url :type "text/html"}]
                [::atom/link {:href canonical-url :rel "self"}]
                [::atom/link {:href (:first navs) :rel "first"}]
                [::atom/link {:href (:last navs) :rel "last"}]
                (when-let [next (:next navs)]
                  [::atom/link {:href next :rel "next"}])
                (when-let [prev (:prev navs)]
                  [::atom/link {:href prev :rel "previous"}])
                [::atom/updated (->> entries
                               (map (comp iso-datetime updated first))
                               sort
                               reverse
                               first)]
                [::atom/id base-url]

                (when global-author
                  [::atom/author
                   [::atom/name global-author]
                   (when global-author-email
                     [::atom/email global-author-email])])

                (for [{:keys [uuid canonical-url title author
                              author-email category tags content] :as post} entries
                      :let [author (or author global-author)
                            author-email (or author-email global-author-email)]]
                  [::atom/entry
                   [::atom/id (str "urn:uuid:" uuid)]
                   [::atom/title title]
                   (when canonical-url
                     [::atom/link {:href canonical-url :type "text/html" :title title :rel "alternate"}])
                   [::atom/published (iso-datetime (published post))]
                   [::atom/updated (iso-datetime (updated post))]
                   [::atom/content {:type "html" :xml:base canonical-url} (str content)]
                   [::atom/author
                    [::atom/name author]
                    (when author-email [::atom/email author-email])]
                   (for [tag tags]
                   ;; FIXME: post-image media:thumbnail
                     [::atom/category {:term tag}])])]))]

    (assoc entry :rendered atom)))
