(ns io.perun.example.post
  (:use [hiccup.core :only (html)]
        [hiccup.page :only (html5)]))


(defn render [global-meta posts post]
  (html5 {:lang "en" :itemtype "http://schema.org/Blog"}
    [:head
      [:meta {:charset "utf-8"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]     ]
    [:body
        [:h1 (:name post)]
        [:nav
          [:ul
            (for [page posts]
              [:li (:name page)])]]]))
