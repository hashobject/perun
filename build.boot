(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src" "resources"}
  :dependencies '[[boot/core "2.1.2" :scope "provided"]
                  [adzerk/bootlaces "0.1.9" :scope "test"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]
                  [endophile "0.1.2" :scope "test"]
                  [circleci/clj-yaml "0.5.3" :scope "test"]
                  [time-to-read "0.1.0" :scope "test"]
                  [sitemap "0.2.4" :scope "test"]
                  [clj-rss "0.1.9" :scope "test"]
                  [gravatar "0.1.0" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])


(def +version+ "0.1.3-SNAPSHOT")
(bootlaces! +version+)

(task-options!
  aot {:all true}
  pom {:project 'perun
       :version +version+
       :description "Static site generation build with Clojure and Boot"
       :url         "https://github.com/hashobject/perun"
       :scm         {:url "https://github.com/hashobject/perun"}
       :license     {"name" "Eclipse Public License"
                     "url"  "http://www.eclipse.org/legal/epl-v10.html"}})


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
        (ttr)
        (slug)
        (permalink)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        ;(render :renderer renderer)
        ;(collection :renderer index-renderer :page "index.html" :filter identity)
        (sitemap :filename "sitemap.xml")
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
        (notify)))

(deftask release-snapshot
  "Release snapshot"
  []
  (comp (build-jar) (push-snapshot)))


(deftask dev
  "Dev process"
  []
  (comp
    (watch)
    (repl :server true)
    (pom)
    (jar)
    (install)))
