(ns io.perun.contrib.images-meta
  (:require [boot.util                 :as u]
            [io.perun.core             :as perun]
            [clojure.java.io           :as io]
            [clojure.string            :as str]
            [boot.from.me.raynes.conch :as sh]
            [image-resizer.util        :as iu]))

(defn substr-between
  "Find string that is nested in between two strings. Return first match.
  Copied from https://github.com/funcool/cuerdas"
  [s prefix suffix]
  (cond
    (nil? s) nil
    (nil? prefix) nil
    (nil? suffix) nil
    :else
    (some-> s
            (str/split prefix)
            second
            (str/split suffix)
            first)))

(defn colors-sh [file]
  (sh/stream-to-string (sh/proc "convert" (:full-path file) "-depth" "4" "+dither" "-colors" "7" "-unique-colors" "txt:-") :out))

(defn get-dimensions [file]
  (let [io-file (-> file :full-path io/file)
        buffered-image (iu/buffered-image io-file)
        dimensions (iu/dimensions buffered-image)]
    dimensions))

(defn process-file [file options]
  (u/info "Processing image %s\n" (:path file))
  (let [colors-output (colors-sh file)
        lines (clojure.string/split colors-output #"\n")
        color-lines (rest lines)
        ; sample line 0,0: (8231,7613,7139)  #201E1C  srgb(32,30,28)
        colors (map
                (fn [line]
                  (str "#"
                    (substr-between line #" #" #" "))
                ) color-lines)
        dimensions (get-dimensions file)
        width (first dimensions)
        height (second dimensions)]
      (u/dbug "\ncolors : %s" (prn-str colors))
      (u/dbug "\nwidth : %s" width)
      (u/dbug "\nheight : %s" height)
      (assoc file :width width
                  :height height
                  :colors colors)))


(defn images-meta [files options]
  (let [updated-files (doall (map #(process-file % options) files))]
    (u/info "Processed %s image files\n" (count files))
    updated-files))
