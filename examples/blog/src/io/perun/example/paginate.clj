(ns io.perun.example.paginate
  (:require [hiccup.page :refer [html5]]))

(defn render [{global-meta :meta posts :entries entry :entry}]
  (html5 {:lang "en" :itemtype "http://schema.org/Blog"}
    [:head
      [:title (str (:site-title global-meta) "|" (:tag entry))]
      [:meta {:charset "utf-8"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]]
    [:body
     [:h1 (str "Page " (:page entry))]
     [:ul.items.columns.small-12
      (for [post posts]
        [:li (:title post)])]]))
