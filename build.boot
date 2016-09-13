(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src"}
  :dependencies '[[boot/core "2.6.0" :scope "provided"]
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
                  [org.asciidoctor/asciidoctorj "1.5.4" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.4.0-SNAPSHOT")

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

(deftask dev
  "Dev process"
  []
  (comp
    (watch)
    (repl :server true)
    (pom)
    (jar)
    (install)))
