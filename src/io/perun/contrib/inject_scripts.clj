(ns io.perun.contrib.inject-scripts
  (:require [boot.util     :as u]
            [clojure.java.io :as io]
            [io.perun.core :as perun]))

(defn inject [html scripts]
  (reduce
    #(.replaceFirst %1 "</head>" (format "<script>%s</script></head>" %2))
    html
    scripts))


(defn inject-scripts [scripts in-path out-path]
  (let [html (-> in-path io/file slurp)
        updated-html (inject html scripts)]
    (u/info "Injected JS scripts %s into %s\n" scripts in-path)
    (spit (io/file out-path) updated-html)))
