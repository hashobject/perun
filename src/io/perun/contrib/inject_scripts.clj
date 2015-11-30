(ns io.perun.contrib.inject-scripts
  (:require [clojure.java.io :as io]
            [io.perun.core   :as perun]))

(defn inject [html scripts]
  (reduce
    #(.replaceFirst %1 "</head>" (format "<script>%s</script></head>" %2))
    html
    scripts))

(defn inject-scripts [scripts in-path out-path]
  (let [html (-> in-path io/file slurp)
        updated-html (inject html scripts)]
    (perun/report-info "inject-scripts" "injected JS scripts %s into %s" scripts in-path)
    (spit (io/file out-path) updated-html)))
