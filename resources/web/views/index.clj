;; Based on ns blog.hashobject.views.index

(ns web.views.index
  (:use [hiccup.core :only (html)]
        [hiccup.page :only (html5 include-css include-js)])
  (:require [clojure.string   :as str]))

;; This page adheres to the asciidoc hooks for applying css markup

(defn render-page [page]
  [:li
   [:p
    [:a {:href (str/join (list (str (:slug page)) ".html"))} (:name page)]]])

(defn render [global-meta pages]
  (html5 {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
          [:title "Generated documents"]
          (include-css "css/asciidoc.css")]
         [:body {:class "article"}
          [:div {:id "header"}
           [:h1 "Generated documents"]
           [:div {:id "content"}
            [:div {:class "paragraph"}
             [:div {:class "ulist compact"}
              [:div {:class "title"} "Pages"]
              [:ul
               (for [page pages] (render-page page))]]]]
           [:div {:id "footer"}
            [:div {:id "footer-text"} "This page was generated using Perun."]]
           ]]))
