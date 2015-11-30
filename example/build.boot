(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[perun "0.2.1-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]])

(require '[io.perun :refer :all]
         '[io.perun.example.index :as index-view]
         '[io.perun.example.post :as post-view]
         '[pandeiro.boot-http :refer [serve]]
         '[jeluard.boot-notify :refer [notify]])

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp
        (global-metadata)
        (images-dimensions)
        (images-resize)
        (base)

        (markdown)
        (draft)
        ;(dump-meta)
        (slug)
        (ttr)
        (word-count)
        (permalink)
        (canonical-url)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        (render :renderer 'io.perun.example.post/render)
        (collection :renderer 'io.perun.example.index/render :page "index.html" :filter identity)
        (inject-scripts :scripts #{"start.js"})
        (sitemap)
        (rss :site-description "Hashobject blog")
        (atom-feed  :site-title "Hashobject" :base-url "http://blog.hashobject.com")
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve :resource-root "public")))
