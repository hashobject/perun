(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[perun "0.4.1-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [pandeiro/boot-http "0.6.3-SNAPSHOT"]])

(require '[clojure.string :as str]
         '[io.perun :refer :all]
         '[io.perun.example.index :as index-view]
         '[io.perun.example.post :as post-view]
         '[pandeiro.boot-http :refer [serve]])

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp
        (global-metadata)
        (markdown)
        (draft)
        (print-meta)
        (slug)
        (ttr)
        (word-count)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        (render :renderer 'io.perun.example.post/render)
        (collection :renderer 'io.perun.example.index/render :page "index.html")
        (paginate :renderer 'io.perun.example.paginate/render)
        (assortment :renderer 'io.perun.example.assortment/render
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
        (static :renderer 'io.perun.example.about/render :page "about.html")
        (inject-scripts :scripts #{"start.js"})
        (sitemap)
        (rss :description "Hashobject blog")
        (atom-feed :filterer :original)
        (print-meta)
        (target)
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve :resource-root "public")))
