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

(defn ^String new-filename [file-path resolution]
  (str (perun/filename file-path)
       "_"
       resolution
       "."
      (perun/extension file-path)))

(defn ^String new-image-filepath [file-path filename new-filename]
  (perun/relativize-url
   (str (perun/parent-path file-path filename)
        "/"
        new-filename)))

(defn write-file [options tmp file ^BufferedImage buffered-file resolution]
  (let [filepath (:path file)
        filename (:filename file)
        new-filename (new-filename filepath resolution)
        filepath-with-resolution (new-image-filepath filepath filename new-filename)
        image-filepath (perun/create-filepath (:out-dir options) filepath-with-resolution)
        new-file (io/file tmp image-filepath)]
    (io/make-parents new-file)
    (ImageIO/write buffered-file (:extension file) new-file)
    {:short-name (perun/filename new-filename)
     :filename new-filename
     :path image-filepath}))

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
