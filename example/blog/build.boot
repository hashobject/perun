(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[perun "0.4.3-SNAPSHOT" :scope "test"]
                  [hiccup "1.0.5" :exclusions [org.clojure/clojure]]
                  [pandeiro/boot-http "0.8.3" :exclusions [org.clojure/clojure]]])

(require '[clojure.string :as str]
         '[io.perun :as perun]
         '[io.perun.example.index :as index-view]
         '[io.perun.example.post :as post-view]
         '[pandeiro.boot-http :refer [serve]])

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp
        (perun/global-metadata)
        (perun/markdown)
        (perun/draft)
        (perun/print-meta)
        (perun/slug)
        (perun/ttr)
        (perun/word-count)
        (perun/build-date)
        (perun/gravatar :source-key :author-email :target-key :author-gravatar)
        (perun/render :renderer 'io.perun.example.post/render)
        (perun/collection :renderer 'io.perun.example.index/render :page "index.html")
        (perun/tags :renderer 'io.perun.example.tags/render)
        (perun/paginate :renderer 'io.perun.example.paginate/render)
        (perun/assortment :renderer 'io.perun.example.assortment/render
                          :grouper (fn [entries]
                                     (->> entries
                                          (mapcat (fn [entry]
                                                    (if-let [kws (:keywords entry)]
                                                      (map #(-> [% entry]) (str/split kws #"\s*,\s*"))
                                                      [])))
                                          (reduce (fn [result [kw entry]]
                                                    (let [path (str kw ".html")]
                                                      (-> result
                                                          (update-in [path :entries] conj entry)
                                                          (assoc-in [path :entry :keyword] kw))))
                                                  {}))))
        (perun/static :renderer 'io.perun.example.about/render :page "about.html")
        (perun/inject-scripts :scripts #{"start.js"})
        (perun/sitemap)
        (perun/rss :description "Hashobject blog")
        (perun/atom-feed :filterer :original)
        (perun/print-meta)
        (target)
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve :resource-root "public")))
