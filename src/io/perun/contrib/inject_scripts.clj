(ns io.perun.contrib.inject-scripts
  (:require [clojure.java.io :as io]
            [io.perun.core   :as perun]))

(defn inject [html scripts]
  (reduce
    #(.replaceFirst %1 "</body>" (format "<script>%s</script></body>" %2))
    html
    scripts))

(defn inject-scripts [scripts in-path out-path]
  (let [html (-> in-path io/file slurp)
        updated-html (inject html scripts)]
    (spit (io/file out-path) updated-html)))
