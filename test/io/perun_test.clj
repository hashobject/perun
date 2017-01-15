(ns io.perun-test
  (:require [boot.core :as boot :refer [deftask]]
            [boot.test :as boot-test :refer [deftesttask]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [io.perun :as p]
            [io.perun.meta :as pm])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

(defn meta=
  [fileset file key val]
  (= (->> file (pm/meta-from-file fileset) key) val))

(defn meta-contains?
  [fileset file key val]
  (let [meta (->> file (pm/meta-from-file fileset) key)]
    (or (some #{val} meta)
        (contains? meta val))))

(deftask key-test
  [p path PATH str "path of the file to test"
   k key  KEY  kw  "the key to test"
   m msg  MSG  str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (contains? (pm/meta-from-file fileset file) key) msg))))

(deftask value-test
  [p path     PATH    str "path of the file to test"
   v value-fn VALUEFN edn "the value to test (optional)"
   m msg      MSG     str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (and (not (nil? file)) (value-fn fileset file)) msg))))

(deftask content-test
  [p path    PATH    str "path of the file to test"
   c content CONTENT str "The content of the file"
   m msg     MSG     str "message shown on failure"]
  (boot/with-pass-thru fileset
    (is (.contains (slurp (boot/tmp-file (boot/tmp-get fileset path))) content) msg)))

(deftask file-exists?
  [p path    PATH str  "path of the image to add"
   n negate?      bool "true to check if file doesn't exist"
   m msg     MSG  str  "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [f (if negate? nil? (complement nil?))]
      (is (f (boot/tmp-get fileset path)) msg))))

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
        (value-test :path "test-image.jpg"  :value-fn #(meta= %1 %2 :width 10))
        (value-test :path "test-image.jpg"  :value-fn #(meta= %1 %2 :height 10))
        (value-test :path "test-image.jpg"  :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions))
        (value-test :path "test-image.jpeg" :value-fn #(meta= %1 %2 :width 54))
        (value-test :path "test-image.jpeg" :value-fn #(meta= %1 %2 :height 180))
        (value-test :path "test-image.jpeg" :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions))
        (value-test :path "test-image.png"  :value-fn #(meta= %1 %2 :width 76))
        (value-test :path "test-image.png"  :value-fn #(meta= %1 %2 :height 37))
        (value-test :path "test-image.png"  :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions))))

(deftesttask images-resize-test []
  (comp (add-image :path "test-image.jpg" :type "JPG" :width 10 :height 10)
        (p/images-resize :resolutions #{100 200})
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta= %1 %2 :width 100))
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta= %1 %2 :height 100))
        (value-test :path "public/test-image_100.jpg" :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta= %1 %2 :width 200))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta= %1 %2 :height 200))
        (value-test :path "public/test-image_200.jpg" :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize))))

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
          (is (= (:global (pm/+global-meta-key+ (meta fileset))) "metadata!")
              "global metadata should be set from perun.base.edn"))))

(defn render
  [data]
  (str "<body>" (:content (:entry data)) "</body>"))

(deftesttask default-tests []
  (comp (add-txt-file :path "2017-01-01-test.md" :content md-content)
        (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"}))
        (p/markdown)

        (testing "markdown"
          (value-test :path "2017-01-01-test.md"
                      :value-fn #(meta= %1 %2 :parsed parsed-md)
                      :msg "`markdown` should set `:parsed` metadata on markdown file")
          (content-test :path "public/2017-01-01-test.html"
                        :content parsed-md
                        :msg "`markdown` should populate HTML file with parsed content"))

        (p/ttr)
        (testing "ttr"
          (value-test :path "public/2017-01-01-test.html"
                      :value-fn #(meta= %1 %2 :ttr 1)
                      :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count)
        (testing "word-count"
          (value-test :path "public/2017-01-01-test.html"
                      :value-fn #(meta= %1 %2 :word-count 18)
                      :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email :target-key :gravatar)
        (testing "gravatar"
          (value-test :path "public/2017-01-01-test.html"
                      :value-fn
                      #(meta= %1 %2 :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                      :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date)
        (testing "build-date"
          (key-test :path "public/2017-01-01-test.html"
                    :key :date-build
                    :msg "`build-date` should set `:date-build` metadata"))

        (p/slug)
        (testing "slug"
          (value-test :path "public/test.html"
                      :value-fn #(meta= %1 %2 :slug "test")
                      :msg "`:slug` should move a file"))

        (p/permalink)
        (testing "permalink"
          (value-test :path "public/test/index.html"
                      :value-fn #(meta= %1 %2 :permalink "/test/")
                      :msg "`permalink` should move a file"))
        (testing "canonical-url"
          (value-test :path "public/test/index.html"
                      :value-fn #(meta= %1 %2 :canonical-url "http://example.com/test/")
                      :msg "`canonical-url` should be implicitly set"))
        (p/sitemap)
        (testing "sitemap"
          (file-exists? :path "public/sitemap.xml"
                        :msg "`sitemap` should write sitemap.xml"))

        (p/rss)
        (testing "rss"
          (file-exists? :path "public/feed.rss"
                        :msg "`rss` should write feed.rss"))

        (p/atom-feed)
        (testing "atom-feed"
          (file-exists? :path "public/atom.xml"
                        :msg "`atom-feed` should write atom.xml"))

        (p/render :renderer 'io.perun-test/render)

        (add-txt-file :path "test.js" :content js-content)
        (p/inject-scripts :scripts #{"test.js"})
        (testing "inject-scripts"
          (content-test :path "public/test/index.html"
                        :content (str "<script>" js-content "</script>")
                        :msg "`inject-scripts` should alter the contents of a file"))

        (p/draft)
        (testing "draft"
          (file-exists? :path "public/test/index.html"
                        :negate? true
                        :msg "`draft` should remove files"))))

(deftesttask content-tests []
  (comp (testing "Collection works without input files" ;; #77
          (p/collection :renderer 'io.perun-test/render))

        (add-txt-file :path "test.md" :content md-content)
        (p/markdown) ;; render once

        (add-txt-file :path "test.md" :content (str/replace md-content #"Hello" "Salutations"))
        (p/markdown)
        (testing "detecting content changes"
          (content-test :path "public/test.html"
                        :content "Salutations"
                        :msg "content changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: false"))
        (p/markdown)
        (testing "detecting metadata changes"
          (value-test :path "public/test.html"
                      :value-fn #(meta= %1 %2 :draft false)
                      :msg "metadata changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: true\nfoo: bar"))
        (p/markdown)
        (testing "detecting metadata additions"
          (value-test :path "public/test.html"
                      :value-fn #(meta= %1 %2 :foo "bar")
                      :msg "metadata additions should result in re-rendering"))

        (add-txt-file :path "test.md" :content md-content)
        (p/markdown)
        (testing "detecting metadata deletions"
          (value-test :path "public/test.html"
                      :value-fn #(meta= %1 %2 :foo nil)
                      :msg "metadata deletions should result in re-rendering"))

        (add-txt-file :path "test2.md" :content md-content)
        (p/markdown)
        (testing "detecting new files"
          (content-test :path "public/test2.html"
                        :content parsed-md
                        :msg "new files should be parsed, after initial render")
          (value-test :path "test2.md"
                      :value-fn #(meta= %1 %2 :parsed parsed-md)
                      :msg "new files should have `:parsed` set on them, after initial render"))))
