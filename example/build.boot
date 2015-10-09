(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src" "resources"}
  :dependencies '[[perun "0.1.3-SNAPSHOT"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]])



(require '[io.perun :refer :all]
         '[jeluard.boot-notify :refer [notify]])


; testing functions
(defn renderer [global data]
  (:content data))

(defn index-renderer [global files]
  (let [names (map :name files)]
    (clojure.string/join "\n" names)))

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp (markdown)
        (draft)
        (dump-meta)
        (ttr)
        (slug)
        ;(permalink)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        ;(render :renderer renderer)
        ;(collection :renderer index-renderer :page "index.html" :filter identity)
        (sitemap :filename "sitemap.xml")
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
        (atom-feed  :title "Hashobject" :subtitle "Hashobject blog" :link "http://blog.hashobject.com")
        (notify)))
