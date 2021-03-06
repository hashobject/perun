(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[perun "0.4.2-SNAPSHOT" :scope "test"]
                 [hiccup "2.0.0-alpha1" :exclusions [org.clojure/clojure] :scope "test"]
                 [cljsjs/semantic-ui "2.2.13-0" :scope "test"]
                 [cljsjs/jquery "3.2.1-0" :scope "test"]
                 [deraen/boot-livereload "0.2.1"]
                 [pandeiro/boot-http "0.8.3" :scope "test"]
                 [degree9/boot-npm "1.10.0-SNAPSHOT"]])

(require '[clojure.string :as str]
         '[clojure.pprint :as pprint]
         '[clojure.java.io :as io]
         '[boot.util :as u]
         '[io.perun :as perun]
         '[io.perun.core :as pcore]
         '[io.perun.meta :as pmeta]
         '[degree9.boot-npm :as npm]
         '[deraen.boot-livereload :refer [livereload]]
         '[pandeiro.boot-http :refer [serve]])

;; Some common fields
(def serve-dir "public/")
(def blog-dir "blog")
(def css-dir "css")
(def site-title "Blog sample with Semantic-UI")
(def site-description "A very good description.")

(deftask include-js
  []
  (comp
   (sift :add-jar #{['cljsjs/jquery #"jquery.min.inc.js"]})
   (sift :add-jar #{['cljsjs/semantic-ui #"semantic.min.inc.js"]})
   (sift :move {#".*/(.*\.js)" (str serve-dir "js/$1")})))

(deftask blog-less
  "Compile and prepare Semantic UI css files"
  []
  (comp
   (npm/npm :install ["less"] :cache-key ::ar-website)
   (perun/lessc :filterer #(= "blog.less" (:filename %)))
   (sift :move {#"(^css/.*)" (str serve-dir "$1")})))

(deftask exclude-unwanted
  []
  (sift :include #{#".*node_modules/"
                   #".*\.less"
                   #".*\.edn"
                   #"semantic-ui-less"
                   #"package\.js.*"
                   #".*\.variables"
                   #".*\.overrides"
                   #".*theme\.config"
                   #"cljsjs/jquery"
                   #"cljsjs/semantic-ui"}
        :invert true))

(deftask perun
  "Build test blog. This task is just for testing different plugins together."
  [s stage VAL kw "The stage value - :dev, :staging or :prod"]
  (comp
   (perun/global-metadata)
   (perun/markdown :md-exts {:tables true :supress-all-html true})
   (perun/draft)
   (perun/slug)
   (perun/ttr)
   (perun/word-count)
   (perun/build-date)
   (perun/collection :renderer 'io.perun.example.index/render :page "index.html")
   (perun/sitemap :filterer #(not= (:slug %) "404"))))

;; For a fully fledged blog see also:
;; https://github.com/bhagany/nicerthantriton.com/blob/master/build.boot

(deftask dev
  [s stage VAL kw "The stage value - :dev, :staging or :prod"]
  (comp (include-js)
        (watch)
        (perun :stage (or stage :dev))
        (perun/print-meta :extensions [".html" ".css" ".png" ".md" ".xml" ".rss"])
        (blog-less)
        (livereload :snippet false :asset-path "public")
        (serve :resource-root "public")
        (notify :audible true)))

(deftask build
  [s stage VAL kw "The stage value - :dev, :staging or :prod"]
  (let [stage (or stage :staging)]
    (comp (include-js)
          (perun :stage stage)
          (blog-less)
          (exclude-unwanted))))
