(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.6.0"]
                 [adzerk/bootlaces "0.1.9" :scope "test"]
                 [hiccup "1.0.5"]
                 [sitemap "0.2.4"]
                 [clj-time "0.9.0"]
                 [clj-rss "0.1.9"]
                 [jeluard/boot-notify "0.1.2" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])


(def +version+ "0.1.0")
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
(require '[io.perun.sitemap :refer :all])
(require '[jeluard.boot-notify :refer [notify]])

(deftask build
  "Build blog."
  []
  (comp (markdown)
        (draft)
        (ttr)
        (sitemap)
        (notify)))
