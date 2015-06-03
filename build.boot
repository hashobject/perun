(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [adzerk/bootlaces "0.1.9" :scope "test"]
                  [clj-time "0.9.0"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])


(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
  pom {:project 'perun
       :version +version+
       :description "Static site generation build with Clojure and Boot"
       :url         "https://github.com/hashobject/perun"
       :scm         {:url "https://github.com/hashobject/perun"}
       :license     {"name" "Eclipse Public License"
                     "url"  "http://www.eclipse.org/legal/epl-v10.html"}})


(require '[io.perun.markdown :refer :all])
(require '[io.perun.ttr :refer :all])
(require '[io.perun.draft :refer :all])
(require '[io.perun.permalink :refer :all])
(require '[io.perun.sitemap :refer :all])
(require '[io.perun.rss :refer :all])
(require '[io.perun.render :refer :all])
(require '[io.perun.collection :refer :all])

(require '[jeluard.boot-notify :refer [notify]])


; testing functions
(defn renderer [data] (:name data))

(defn index-renderer [files]
  (let [names (map :name files)]
    (clojure.string/join "\n" names)))

(deftask build
  "Build test blog. This task is just for testing different plugins together."
  []
  (comp (markdown)
        (draft)
        (ttr)
        (permalink)
        (render :renderer renderer)
        (collection :renderer index-renderer :page "index.html" :filter identity)
        (sitemap :filename "sitemap.xml")
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
        (notify)))


(deftask install-locally
  "Install locally"
  []
  (comp (pom) (jar) (install)))

(deftask release-snapshot
  "Release snapshot"
  []
  (comp (pom) (jar) (push-snapshot)))