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
  [file key val]
  (= (-> file pm/+meta-key+ key) val))

(defn meta-contains?
  [file key val]
  (or (some #{val} (-> file pm/+meta-key+ key))
      (contains? (-> file pm/+meta-key+ key) val)))

(deftask prn-meta-key
  [p path PATH str "path of the file to test"
   k key  KEY  kw  "the key to prn"]
  (boot/with-pass-thru fileset
    (->> path (boot/tmp-get fileset) pm/+meta-key+ key prn)))

(deftask key-test
  [p path PATH str "path of the file to test"
   k key  KEY  kw  "the key to test"
   m msg  MSG  str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (contains? (pm/+meta-key+ file) key) msg))))

(deftask value-test
  [p path     PATH    str "path of the file to test"
   v value-fn VALUEFN edn "the value to test (optional)"
   m msg      MSG     str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (and (not (nil? file)) (value-fn file)) msg))))

(deftask content-test
  [p path    PATH    str  "path of the file to test"
   c content CONTENT str  "The content of the file"
   n negate?         bool "true to check if file doesn't exist"
   m msg     MSG     str  "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [f (if negate? not identity)]
      (is (f (.contains (slurp (boot/tmp-file (boot/tmp-get fileset path))) content))))))

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

This --- be ___markdown___.")

(def parsed-md-basic "<h1><a href=\"#hello-there\" name=\"hello-there\"></a>Hello there</h1>\n<p>This --- be <strong><em>markdown</em></strong>.</p>")

(def parsed-md-smarts "<h1><a href=\"#hello-there\" name=\"hello-there\"></a>Hello there</h1>\n<p>This &mdash; be <strong><em>markdown</em></strong>.</p>")

(def js-content "(function somejs() { console.log('foo'); })();")

(deftesttask global-metadata-test []
  (comp (add-txt-file :path "perun.base.edn" :content "{:global \"metadata!\"}")
        (p/global-metadata)
        (boot/with-pass-thru fileset
          (is (= (:global (pm/+global-meta-key+ (meta fileset))) "metadata!")))))

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
          (comp
           (value-test :path "2017-01-01-test.md"
                       :value-fn #(meta= % :parsed parsed-md-basic)
                       :msg "`markdown` should set `:parsed` metadata on markdown file")
           (content-test :path "2017-01-01-test.html"
                         :content parsed-md-basic
                         :msg "`markdown` should populate HTML file with parsed content")))

        (p/ttr)
        (testing "ttr"
          (value-test :path "2017-01-01-test.html"
                      :value-fn #(meta= % :ttr 1)
                      :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count)
        (testing "word-count"
          (value-test :path "2017-01-01-test.html"
                      :value-fn #(meta= % :word-count 19)
                      :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email :target-key :gravatar)
        (testing "gravatar"
          (value-test :path "2017-01-01-test.html"
                      :value-fn
                      #(meta= % :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                      :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date)
        (testing "build-date"
          (key-test :path "2017-01-01-test.html"
                    :key :date-build
                    :msg "`build-date` should set `:date-build` metadata"))

        (p/slug)
        (testing "slug"
          (value-test :path "2017-01-01-test.html"
                      :value-fn #(meta= % :slug "test")
                      :msg "`slug` should set `:slug` metadata"))

        (p/permalink)
        (testing "permalink"
          (value-test :path "2017-01-01-test.html"
                      :value-fn #(meta= % :permalink "/test/index.html")
                      :msg "`permalink` should set `:permalink` metadata"))

        (p/canonical-url)
        (testing "canonical-url"
          (value-test :path "2017-01-01-test.html"
                      :value-fn #(meta= % :canonical-url "http://example.com/test/index.html")
                      :msg "`canonical-url` should set `:canonical-url` metadata"))

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
          (file-exists? :path "2017-01-01-test.html"
                        :negate? true
                        :msg "`draft` should remove files"))))

(deftesttask with-arguments-test []
  (comp (add-txt-file :path "test.md" :content md-content)
        (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"}))
        (p/markdown :meta {:markdown-set :metadata} :options {:extensions {:smarts true}})
        (testing "markdown"
          (comp
           (value-test :path "test.md"
                       :value-fn #(meta= % :parsed parsed-md-smarts)
                       :msg "`markdown` should set `:parsed` metadata on markdown file")
           (content-test :path "test.html"
                         :content parsed-md-smarts
                         :msg "`markdown` should populate HTML file with parsed content")))

        (p/ttr :filterer :markdown-set)
        (testing "ttr"
          (value-test :path "test.md"
                      :value-fn #(meta= % :ttr 1)
                      :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count :filterer :markdown-set)
        (testing "word-count"
          (value-test :path "test.md"
                      :value-fn #(meta= % :word-count 19)
                      :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email :target-key :gravatar :filterer :markdown-set)
        (testing "gravatar"
          (value-test :path "test.md"
                      :value-fn
                      #(meta= % :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                      :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date :filterer :markdown-set)
        (testing "build-date"
          (key-test :path "test.md"
                    :key :date-build
                    :msg "`build-date` should set `:date-build` metadata"))

        (p/slug :filterer :markdown-set :slug-fn (fn [filename] (->> (str/split filename #"\.")
                                                                     drop-last
                                                                     str/join
                                                                     str/lower-case)))

        (testing "slug"
          (value-test :path "test.md"
                      :value-fn #(meta= % :slug "test")
                      :msg "`slug` should set `:slug` metadata"))

        (p/permalink :filterer :markdown-set :permalink-fn (fn [_] "/foo.html"))
        (testing "permalink"
          (value-test :path "test.md"
                      :value-fn #(meta= % :permalink "/foo.html")
                      :msg "`permalink` should set `:permalink` metadata"))

        (p/canonical-url :filterer :markdown-set)
        (testing "canonical-url"
          (value-test :path "test.md"
                      :value-fn #(meta= % :canonical-url "http://example.com/foo.html")
                      :msg "`canonical-url` should set `:canonical-url` metadata"))

        (p/sitemap :filterer :markdown-set :filename "test.xml" :out-dir "foo" :url "http://bar.com/")
        (testing "sitemap"
          (file-exists? :path "foo/test.xml"
                        :msg "`sitemap` should write test.xml"))

        (p/rss :filterer :markdown-set
               :filename "test.rss"
               :out-dir "foo"
               :base-url "http://bar.com/"
               :site-title "Test Site"
               :description "Here we go a-testing")
        (testing "rss"
          (file-exists? :path "foo/test.rss"
                        :msg "`rss` should write test.rss"))

        (p/atom-feed :filterer :markdown-set
                     :filename "test-atom.xml"
                     :out-dir "foo"
                     :base-url "http://bar.com/"
                     :site-title "Test Site"
                     :subtitle "Sub-test"
                     :description "Here we go a-testing")
        (testing "atom-feed"
          (file-exists? :path "foo/test-atom.xml"
                        :msg "`atom-feed` should write test-atom.xml"))

        (p/render :renderer 'io.perun-test/render
                  :filterer :markdown-set
                  :out-dir "bar"
                  :meta {:set-by-render true})

        (add-txt-file :path "test.js" :content js-content)
        (add-txt-file :path "baz.html" :content "<body></body>")
        (p/inject-scripts :scripts #{"test.js"} :filter #{#"foo"})
        (p/inject-scripts :scripts #{"test.js"} :remove #{#"baz"})
        (testing "inject-scripts"
          (comp
           (content-test :path "bar/foo.html"
                         :content (str "<script>" js-content "</script>")
                         :msg "`inject-scripts` should alter the contents of a file")
           (content-test :path "baz.html"
                         :content (str "<script>" js-content "</script>")
                         :negate? true
                         :msg "`inject-scripts` should not alter the contents of a removed file")))))

(deftesttask content-tests []
  (comp (testing "Collection works without input files" ;; #77
          (p/collection :renderer 'io.perun-test/render))

        (add-txt-file :path "test.md" :content md-content)
        (p/markdown) ;; render once

        (add-txt-file :path "test.md" :content (str/replace md-content #"Hello" "Salutations"))
        (p/markdown)
        (testing "detecting content changes"
          (content-test :path "test.html" :content "Salutations"))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: false"))
        (p/markdown)
        (testing "detecting metadata changes"
          (value-test :path "test.html" :value-fn #(meta= % :draft false)))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: true\nfoo: bar"))
        (p/markdown)
        (testing "detecting metadata additions"
          (value-test :path "test.html" :value-fn #(meta= % :foo "bar")))

        (add-txt-file :path "test.md" :content md-content)
        (p/markdown)
        (testing "detecting metadata deletions"
          (value-test :path "test.html" :value-fn #(meta= % :foo nil)))

        (add-txt-file :path "test2.md" :content md-content)
        (p/markdown)
        (testing "detecting new files"
          (comp
           (content-test :path "test2.html" :content parsed-md-basic)
           (value-test :path "test2.md" :value-fn #(meta= % :parsed parsed-md-basic))))))
