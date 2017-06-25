(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[perun "0.4.1-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [pandeiro/boot-http "0.6.3-SNAPSHOT"]])

(require '[io.perun :as perun]
         '[io.perun.example.index :as index-view]
         '[io.perun.example.post :as post-view]
         '[pandeiro.boot-http :refer [serve]])

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp
        (perun/global-metadata)
        (perun/images-dimensions)
        (perun/images-resize)
        (perun/base)
        (perun/markdown)
        (perun/draft)
        (perun/print-meta)
        (perun/slug)
        (perun/permalink)
        (perun/canonical-url)
        (perun/build-date)
        (perun/render :renderer 'io.perun.example.post/render)
        (perun/collection :renderer 'io.perun.example.index/render :page "index.html")
        (perun/sitemap)
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve :resource-root "public")))
