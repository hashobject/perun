(ns io.perun.contrib.images-dimensions
  (:require [boot.util                 :as u]
            [io.perun.core             :as perun]
            [clojure.java.io           :as io]
            [image-resizer.util        :as iu]))


(defn get-dimensions [file]
  (let [io-file (-> file :full-path io/file)
        buffered-image (iu/buffered-image io-file)
        dimensions (iu/dimensions buffered-image)]
    dimensions))

(defn process-file [file options]
  (u/info "Processing image %s\n" (:path file))
  (let [dimensions (get-dimensions file)
        width (first dimensions)
        height (second dimensions)]
      (u/dbug "\nwidth : %s" width)
      (u/dbug "\nheight : %s" height)
      (assoc file :width width
                  :height height)))


(defn images-dimensions [files options]
  (let [updated-files (doall (map #(process-file % options) files))]
    (u/info "Processed %s image files\n" (count files))
    updated-files))
