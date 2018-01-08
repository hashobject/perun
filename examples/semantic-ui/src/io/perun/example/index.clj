(ns io.perun.example.index
  (:require [hiccup.page :refer [html5 include-js]]))

(defn render [{meta :meta entries :entries entry :entry :as global-meta}]
  (html5 {:lang "en"}
    [:head
     [:title (:site-title meta)]

     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]

     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]

     [:link {:rel "shortcut icon" :type "image/x-icon" :href "/favicon.ico"}]

     ;; Semantic UI inclusions
     [:link {:rel "stylesheet" :type "text/css" :href "/css/blog.css"}]

     (include-js "/js/jquery.min.inc.js")
     (include-js "/js/semantic.min.inc.js")]

    [:body#site
     "<!-- Header -->"
     [:div.ui.center.aligned.grid.container
      [:div.row
       [:div.column
        [:div.ui.top.sticky.secondary.labeled.icon.menu
         ;; not an .item so that we don't have padding!
         [:a.logo.item {:href "https://perun.io/"}
          [:svg.ui.image {:width 58 :height 58 :version "1.1" :viewbox "0 0 100 100" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns "http://www.w3.org/2000/svg"}
           [:polygon#triangle-1 {:fill-opacity "0.840000033" :fill "#DCF9BB" :points "49.4096257 0 98.8192514 98.3899994 0 98.3899994"}]
           [:polygon#triangle-2 {:fill-opacity "0.59692029" :fill "#EF81E7" :points "62.9854112 2.18787658 99.9963667 75.8882507 25.9744556 75.8882507"}]
           [:polygon#triangle-3 {:fill-opacity "0.377292799" :fill "#465292" :points "56.2399558 45.7867705 78.4465291 90.006995 34.0333825 90.006995"}]]
          ;; [:img.ui.image {:width 58 :height 58 :src "/images/logo512x512.png" :alt (:site-author global-meta)}]]
          [:a.item {:href "https://perun.io/guides/"}
           [:i.book.icon] "Guides"]
          [:a.item {:href "https://github.com/hashobject/perun/wiki"}
           [:i.users.icon] "Community"]
          [:a.item {:href "https://github.com/hashobject/perun"}
           [:i.github.icon] "Github"]]]]]]
     "<!-- End Header -->"

     "<!-- Content -->"
     [:div.ui.centered.main.stackable.reverse.grid.container {:role "content"}
      [:div.ui.divided.relaxed.items
       (for [post (range 5)]
         [:div.ui.item
          "This is an entry"])]]

     "<!-- End Content -->"

     "<!-- Footer -->"
     "<!-- End Footer -->"]))
