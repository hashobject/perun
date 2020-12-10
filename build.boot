(set-env!
 :source-paths #{"test"}
 :resource-paths #{"src"}
 :dependencies '[[boot/core "2.8.2" :scope "provided" :exclusions [org.clojure/clojure]]
                 [degree9/boot-semver "1.10.0" :scope "test" :exclusions [org.clojure/clojure]]])

(require 'io.perun)
(def pod-deps
  (->> (ns-interns 'io.perun)
       vals
       (filter #(:deps (meta %)))
       (map deref)
       (reduce concat)
       (map #(into % [:scope "test" :exclusions '[org.clojure/clojure]]))))

(set-env! :dependencies #(into % pod-deps))

(require '[io.perun-test])
(require '[boot.test :refer [runtests test-report test-exit]])
(require '[degree9.boot-semver :refer :all])

(task-options!
 aot {:all true}
 pom {:project 'powerlaces/perun
      :description "Static site generator build with Clojure and Boot"
      :url         "https://github.com/hashobject/perun"
      :scm         {:url "https://github.com/hashobject/perun"}
      :license     {"name" "Eclipse Public License"
                    "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "Build process"
  []
  (comp
   (version :include true)
   (build-jar)))

(deftask dev
  "Dev process"
  []
  (comp
    (watch)
    (repl :server true)
    (build)))

(ns-unmap *ns* 'test)

(deftask test
  "Run tests"
  []
  (comp
    (runtests)
    (test-report)
    (test-exit)))
