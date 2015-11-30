(ns io.perun.contrib.images-dimensions
  (:require [io.perun.core      :as perun]
            [clojure.java.io    :as io]
            [image-resizer.util :as iu]))


(defn get-dimensions [file]
  (let [io-file (-> file :full-path io/file)
        buffered-image (iu/buffered-image io-file)
        dimensions (iu/dimensions buffered-image)]
    dimensions))

(defn process-file [file options]
  (perun/report-debug "images-dimensions" "processing image" (:path file))
  (let [dimensions (get-dimensions file)
        width (first dimensions)
        height (second dimensions)
        dims {:width width :height height}]
      (perun/report-debug "images-dimensions" "dimensions" dims)
      (merge file dims)))


(defn images-dimensions [files options]
  (let [updated-files (doall (map #(process-file % options) files))]
    (perun/report-info "images-dimensions" "processed %s image files" (count files))
    updated-files))
