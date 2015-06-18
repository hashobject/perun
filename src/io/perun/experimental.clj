(ns io.perun.experimental
  (:require [boot.core :as boot :refer [deftask]]
            [boot.util      :as u]
            [clojure.string :as string]
            [io.perun.core :as perun]))

(defn ^:private default-slug-fn [filename]
  (->> (string/split filename #"[-\.]")
       (drop 3)
       drop-last
       (string/join "-")
       string/lower-case))

(deftask slug
  "Adds :slug key to files metadata. Slug is derived from filename."
  [s slug-fn SLUGFN sym "Function to build slug from filename"]
  (boot/with-pre-wrap fileset
    (let [slug-fn       (or slug-fn default-slug-fn)
          files         (:metadata (meta fileset))
          updated-files (into {}
                              (for [[f m] files]
                                {f (assoc m :slug (slug-fn f)) }))]
      (u/dbug "Generated Slugs:\n%s\n"
              (pr-str (map :slug (vals updated-files))))
      (u/info "Added slugs to %s files\n" (count updated-files))
      (with-meta fileset {:metadata updated-files}))))
