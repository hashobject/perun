;; Based on ns blog.hashobject.views.post

(ns web.views.page
  (:use [hiccup.core :only (html)]
        [hiccup.page :only (html5 include-css include-js)]))

(defn render [global-meta metadata]
  (html5 {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
          [:title (str (:name metadata))]
          (include-css "css/asciidoc.css")]
         [:body {:class "article"}
          [:div {:id "header"}
           [:h1 (str (:name metadata))]
           [:div {:id "details"}
            [:p
             [:a {:href "index.html"} "Back to overview"]]]]
          [:div {:id "content"} ;to align the content via the css structure
           (str (slurp (:include metadata)))]
          [:div {:id "footer"}
           [:div {:id "footer-text"} "This page was generated using Perun."]]]))
