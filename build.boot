(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src"}
  :dependencies '[[boot/core "2.7.2" :scope "provided"]
                  [adzerk/boot-test "1.2.0" :scope "test"]
                  [adzerk/bootlaces "0.1.13" :scope "test"]])

(require 'io.perun)
(def pod-deps
  (->> (ns-interns 'io.perun)
       vals
       (filter #(:deps (meta %)))
       (map deref)
       (reduce concat)
       (map #(conj % :scope "test"))))

(set-env! :dependencies #(into % pod-deps))

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
