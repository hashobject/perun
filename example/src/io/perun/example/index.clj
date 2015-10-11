(ns io.perun.example.index
  (:use [hiccup.core :only (html)]
        [hiccup.page :only (html5)]))


(defn render [global-meta posts]
  (html5 {:lang "en" :itemtype "http://schema.org/Blog"}
    [:head
      [:meta {:charset "utf-8"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]]
    [:body
         [:ul.items.columns.small-12
          (for [post posts]
            [:li (:name post)])]]))
