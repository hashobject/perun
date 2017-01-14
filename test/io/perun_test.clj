(ns io.perun-test
  (:require [boot.core :as boot :refer [deftask]]
            [boot.test :as boot-test :refer [deftesttask]]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is]]
            [io.perun :as p]
            [io.perun.meta :as pm])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

(defn meta=
  [file key val]
  (= (-> file pm/+meta-key+ key) val))

(defn meta-contains?
  [file key val]
  (or (some #{val} (-> file pm/+meta-key+ key))
      (contains? (-> file pm/+meta-key+ key) val)))

(deftask key-test
  [p path     PATH    str "path of the file to test"
   k key      KEY     kw  "the key to test"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (contains? (pm/+meta-key+ file) key)))))

(deftask value-test
  [p path     PATH    str "path of the file to test"
   v value-fn VALUEFN edn "the value to test (optional)"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (and (not (nil? file)) (value-fn file))))))

(deftask content-test
  [p path    PATH    str "path of the file to test"
   c content CONTENT str "The content of the file"]
  (boot/with-pass-thru fileset
    (is (.contains (slurp (boot/tmp-file (boot/tmp-get fileset path))) content))))

(deftask file-exists?
  [p path    PATH str  "path of the image to add"
   n negate?      bool "true to check if file doesn't exist"]
  (boot/with-pass-thru fileset
    (let [f (if negate? nil? (complement nil?))]
      (is (f (boot/tmp-get fileset path))))))

(deftask add-image
  [p path   PATH   str "path of the image to add"
   t type   TYPE   str "kind of image to write"
   w width  WIDTH  int "width of the image"
   g height HEIGHT int "height of the image"]
  (boot/with-pre-wrap fileset
    (let [tmp (boot/tmp-dir!)
          buffered-image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
          file (io/file tmp path)]
      (io/make-parents file)
      (ImageIO/write buffered-image type file)
      (-> fileset
          (boot/add-resource tmp)
          boot/commit!))))

(deftesttask images-dimensions-test []
  (comp (add-image :path "test-image.jpg"  :type "JPG" :width 10 :height 10)
        (add-image :path "test-image.jpeg" :type "JPG" :width 54 :height 180)
        (add-image :path "test-image.png"  :type "PNG" :width 76 :height 37)
        (p/images-dimensions)
        (value-test :path "test-image.jpg"  :value-fn #(meta= % :width 10))
        (value-test :path "test-image.jpg"  :value-fn #(meta= % :height 10))
        (value-test :path "test-image.jpg"  :value-fn #(meta-contains? % :io.perun/trace :io.perun/images-dimensions))
        (value-test :path "test-image.jpeg" :value-fn #(meta= % :width 54))
        (value-test :path "test-image.jpeg" :value-fn #(meta= % :height 180))
        (value-test :path "test-image.jpeg" :value-fn #(meta-contains? % :io.perun/trace :io.perun/images-dimensions))
        (value-test :path "test-image.png"  :value-fn #(meta= % :width 76))
        (value-test :path "test-image.png"  :value-fn #(meta= % :height 37))
        (value-test :path "test-image.png"  :value-fn #(meta-contains? % :io.perun/trace :io.perun/images-dimensions))))

(deftesttask images-resize-test []
  (comp (add-image :path "test-image.jpg" :type "JPG" :width 10 :height 10)
        (p/images-resize :resolutions #{100 200})
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta= % :width 100))
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta= % :height 100))
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta-contains? % :io.perun/trace :io.perun/images-resize))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta= % :width 200))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta= % :height 200))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta-contains? % :io.perun/trace :io.perun/images-resize))))

(deftask add-txt-file
  [p path    PATH  str "path of the file to add"
   c content WIDTH str "the content to write"]
  (boot/with-pre-wrap fileset
    (let [tmp (boot/tmp-dir!)
          file (io/file tmp path)]
      (io/make-parents file)
      (spit file content)
      (-> fileset
          (boot/add-resource tmp)
          boot/commit!))))

(def md-content
  "---
email: brent.hagany@gmail.com
uuid: 2078a34d-1b1a-4257-9eff-ffe215d90bcd
draft: true
author: Testy McTesterson
---
# Hello there

This be ___markdown___.")

(def parsed-md "<h1><a href=\"#hello-there\" name=\"hello-there\"></a>Hello there</h1>\n<p>This be <strong><em>markdown</em></strong>.</p>")

(def js-content "(function somejs() { console.log('foo'); })();")

(deftesttask global-metadata-test []
  (comp (add-txt-file :path "perun.base.edn" :content "{:global \"metadata!\"}")
        (p/global-metadata)
        (boot/with-pass-thru fileset
          (is (= (:global (pm/+global-meta-key+ (meta fileset))) "metadata!")))))

(defn render
  [data]
  (str "<body>" (:content (:entry data)) "</body>"))

(deftesttask markdown-test []
  (comp (add-txt-file :path "2017-01-01-test.md" :content md-content)
        (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"}))
        (p/markdown)
        (content-test :path "2017-01-01-test.html" :content parsed-md)
        (value-test :path "2017-01-01-test.md" :value-fn #(meta= % :parsed parsed-md))

        (p/ttr)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :ttr 1))

        (p/word-count)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :word-count 18))

        (p/gravatar :source-key :email :target-key :gravatar)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444"))

        (p/build-date)
        (key-test :path "2017-01-01-test.html" :key :date-build)

        (p/slug)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :slug "test"))

        (p/permalink)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :permalink "/test/index.html"))

        (p/canonical-url)
        (value-test :path "2017-01-01-test.html" :value-fn #(meta= % :canonical-url "http://example.com/test/index.html"))

        (p/sitemap)
        (file-exists? :path "public/sitemap.xml")

        (p/rss)
        (file-exists? :path "public/feed.rss")

        (p/atom-feed)
        (file-exists? :path "public/atom.xml")

        (p/render :renderer 'io.perun-test/render)

        (add-txt-file :path "test.js" :content js-content)
        (p/inject-scripts :scripts #{"test.js"})
        (content-test :path "public/test/index.html" :content (str "<script>" js-content "</script>"))

        (p/draft)
        (file-exists? :path "2017-01-01-test.html" :negate? true)))
