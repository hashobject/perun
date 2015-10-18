(ns io.perun.contrib.images-resize
  (:require [boot.util                   :as u]
            [io.perun.core               :as perun]
            [clojure.java.io             :as io]
            [clojure.string              :as str]
            [image-resizer.core          :as resize]
            [image-resizer.scale-methods :as scale]
            [image-resizer.util          :as iu])
  (:import
     [java.awt.image BufferedImage]
     [javax.imageio ImageIO ImageWriter]))

(defn ^String new-image-filepath [file-path filename resolution]
  (str (perun/parent-path file-path filename)
       "/"
       (perun/filename file-path)
       "_"
       resolution
       "."
      (perun/extension file-path)))

(defn write-file [options tmp file ^BufferedImage buffered-file resolution]
  (let [filepath (:path file)
        filename (:filename file)
        filepath-with-resolution (new-image-filepath filepath filename resolution)
        image-filepath (perun/create-filepath (:out-dir options) filepath-with-resolution)
        new-file (io/file tmp image-filepath)]
    (io/make-parents new-file)
    (ImageIO/write buffered-file (:extension file) new-file)))

(defn resize-to [tgt-path file options resolution]
  (let [io-file (-> file :full-path io/file)
        buffered-image (iu/buffered-image io-file)
        resized-buffered-image (resize/resize-to-width buffered-image resolution)]
      (write-file options tgt-path file resized-buffered-image resolution)))

(defn process-image [tgt-path file options]
  (u/info "Resizing %s\n" (:path file))
  (let [resolutions (:resolutions options)]
    (doall
      (clojure.core/pmap
        (fn [resolution]
          (resize-to tgt-path file options resolution))
        resolutions))))

(defn images-resize [tgt-path files options]
  (let [updated-files (doall (map #(process-image tgt-path % options) files))]
    (u/info "Processed %s image files\n" (count files))
    updated-files))
