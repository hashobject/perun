(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[perun "0.1.3-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]])

(require '[io.perun :refer :all]
         '[io.perun.example.index :as index-view]
         '[io.perun.example.post :as post-view]
         '[jeluard.boot-notify :refer [notify]])

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp (markdown)
        ;(draft)
        ;(dump-meta)
        (ttr)
        (slug)
        (permalink)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        (render :renderer 'io.perun.example.post/render)
        (collection :renderer 'io.perun.example.index/render :page "index.html" :filter identity)
        (sitemap :filename "sitemap.xml")
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
        (atom-feed  :title "Hashobject" :subtitle "Hashobject blog" :link "http://blog.hashobject.com")
        (notify)))