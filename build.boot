(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src"}
  :dependencies '[[boot/core "2.6.0" :scope "provided"]
                  [adzerk/boot-test "1.1.2" :scope "test"]
                  [adzerk/bootlaces "0.1.13" :scope "test"]
                  [org.pegdown/pegdown "1.6.0" :scope "test"]
                  [circleci/clj-yaml "0.5.5" :scope "test"]
                  [time-to-read "0.1.0" :scope "test"]
                  [sitemap "0.2.5" :scope "test"]
                  [clj-rss "0.2.3" :scope "test"]
                  [gravatar "1.1.1" :scope "test"]
                  [clj-time "0.12.0" :scope "test"]
                  [mvxcvi/puget "1.0.0" :scope "test"]
                  [com.novemberain/pantomime "2.8.0" :scope "test"]
                  [org.clojure/tools.namespace "0.3.0-alpha3" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])
(require '[io.perun.core :refer [+version+]])
(require '[io.perun-test])
(require '[boot.test :refer [runtests]])

(bootlaces! +version+)

(task-options!
  aot {:all true}
  push {:ensure-branch  "master"
        :ensure-clean   false
        :ensure-version +version+}
  pom {:project 'perun
       :version +version+
       :description "Static site generator build with Clojure and Boot"
       :url         "https://github.com/hashobject/perun"
       :scm         {:url "https://github.com/hashobject/perun"}
       :license     {"name" "Eclipse Public License"
                     "url"  "http://www.eclipse.org/legal/epl-v10.html"}})


(deftask release-snapshot
  "Release snapshot"
  []
  (comp (build-jar) (push-snapshot)))

(deftask build
  "Build process"
  []
  (comp
    (pom)
    (jar)
    (install)))

(deftask dev
  "Dev process"
  []
  (comp
    (watch)
    (repl :server true)
    (build)))
