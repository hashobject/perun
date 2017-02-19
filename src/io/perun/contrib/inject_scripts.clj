(ns io.perun.contrib.inject-scripts
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn inject [html scripts]
  (->> scripts
       ;; replace regex special characters
       (map #(str/replace % #"([\\\$])" "\\\\$1"))
       (reduce
        #(.replaceFirst %1 "</body>" (format "<script>%s</script></body>" %2))
        html)))

(defn inject-scripts [{:keys [entry scripts]}]
  (let [file-content (-> entry :full-path io/file slurp)]
    (assoc entry :rendered (inject file-content scripts))))
