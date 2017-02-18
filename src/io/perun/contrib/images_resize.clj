(ns io.perun.contrib.images-resize
  (:require [io.perun.core               :as perun]
            [clojure.java.io             :as io]
            [clojure.string              :as str]
            [image-resizer.core          :as resize]
            [image-resizer.scale-methods :as scale]
            [image-resizer.util          :as iu])
  (:import
     [java.awt.image BufferedImage]
     [javax.imageio ImageIO ImageWriter]))

(defn write-file [options tmp file ^BufferedImage buffered-file resolution]
  (let [{:keys [slug extension parent-path]} file
        new-filename (str slug "_" resolution "." extension)
        new-path (perun/create-filepath (:out-dir options) parent-path new-filename)
        new-file (io/file tmp new-path)]
    (io/make-parents new-file)
    (ImageIO/write buffered-file extension new-file)
    {:path new-path}))

(defn resize-to [tgt-path file options resolution]
  (let [io-file (-> file :full-path io/file)
        buffered-image (iu/buffered-image io-file)
        resized-buffered-image (resize/resize-to-width buffered-image resolution)
        new-dimensions (iu/dimensions resized-buffered-image)
        new-meta (write-file options tgt-path file resized-buffered-image resolution)
        dimensions {:width (first new-dimensions) :height (second new-dimensions)}]
    (merge file new-meta dimensions (select-keys options [:out-dir]))))

(defn process-image [tgt-path file options]
  (perun/report-debug "image-resize" "resizing" (:path file))
  (pmap #(resize-to tgt-path file options %) (:resolutions options)))

(defn images-resize [tgt-path files options]
  (let [updated-files (doall (mapcat #(process-image tgt-path % options) files))]
    (perun/report-info "image-resize" "processed %s image files" (count files))
    updated-files))
