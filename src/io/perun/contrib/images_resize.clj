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

(def img-cache (atom {}))

(defn get-input-img
  [{:keys [input-meta]}]
  (let [input-path (:full-path input-meta)
        key (str input-path "-" (:hash input-meta))]
    (if-let [buffered-image (get @img-cache key)]
      @buffered-image
      (let [buffered-image (future (-> input-path
                                       io/file
                                       iu/buffered-image))]
        (swap! img-cache assoc key buffered-image)
        @buffered-image))))

(defn image-resize
  [{:keys [path resolution extension tmp-dir] :as data}]
  (perun/report-debug "image-resize" "resizing" path)
  (let [buffered-image (get-input-img data)
        resized-buffered-image (resize/resize-to-width buffered-image resolution)
        new-file (io/file tmp-dir path)]
    (io/make-parents new-file)
    (ImageIO/write resized-buffered-image extension new-file)
    (merge (dissoc data :input-meta :tmp-dir)
           (into {} (map vector
                         [:width :height]
                         (iu/dimensions resized-buffered-image))))))
