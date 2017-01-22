(ns io.perun-test
  (:require [boot.core :as boot :refer [deftask]]
            [boot.task.built-in :refer [sift]]
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

(deftask prn-meta-key
  [p path PATH str "path of the file to test"
   k key  KEY  kw  "the key to prn"]
  (boot/with-pass-thru fileset
    (->> path (boot/tmp-get fileset) pm/+meta-key+ key prn)))

(deftask key-check
  [p path PATH str "path of the file to test"
   k key  KEY  kw  "the key to test"
   m msg  MSG  str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (contains? (pm/meta-from-file fileset file) key) msg))))

(deftask value-check
  [p path     PATH    str "path of the file to test"
   v value-fn VALUEFN edn "the value to test (optional)"
   m msg      MSG     str "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [file (boot/tmp-get fileset path)]
      (is (and (not (nil? file)) (value-fn fileset file)) msg))))

(deftask content-check
  [p path    PATH    str  "path of the file to test"
   c content CONTENT str  "The content of the file"
   n negate?         bool "true to check if file doesn't exist"
   m msg     MSG     str  "message shown on failure"]
  (boot/with-pass-thru fileset
    (let [f (if negate? not identity)]
      (is (f (.contains (slurp (boot/tmp-file (boot/tmp-get fileset path))) content)) msg))))

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
        (value-check :path "test-image.jpg"
                     :value-fn #(meta= %1 %2 :width 10)
                     :msg "`images-dimensions should set `:width` metadata on image file")
        (value-check :path "test-image.jpg"
                     :value-fn #(meta= %1 %2 :height 10)
                     :msg "`images-dimensions should set `:height` metadata on image file")
        (value-check :path "test-image.jpg"
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions)
                     :msg "`images-dimensions should add `:io.perun/images-dimensions` to `:io.perun/trace`")
        (value-check :path "test-image.jpeg"
                     :value-fn #(meta= %1 %2 :width 54)
                     :msg "`images-dimensions should set `:width` metadata on image file")
        (value-check :path "test-image.jpeg"
                     :value-fn #(meta= %1 %2 :height 180)
                     :msg "`images-dimensions should set `:height` metadata on image file")
        (value-check :path "test-image.jpeg"
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions)
                     :msg "`images-dimensions should add `:io.perun/images-dimensions` to `:io.perun/trace`")
        (value-check :path "test-image.png"
                     :value-fn #(meta= %1 %2 :width 76)
                     :msg "`images-dimensions should set `:width` metadata on image file")
        (value-check :path "test-image.png"
                     :value-fn #(meta= %1 %2 :height 37)
                     :msg "`images-dimensions should set `:height` metadata on image file")
        (value-check :path "test-image.png"
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-dimensions)
                     :msg "`images-dimensions should add `:io.perun/images-dimensions` to `:io.perun/trace`")))

(deftesttask images-resize-test []
  (comp (add-image :path "test-image.jpg" :type "JPG" :width 10 :height 10)
        (p/images-resize :resolutions #{100 200})
        (value-check :path "public/test-image_100.jpg"
                     :value-fn #(meta= %1 %2 :width 100)
                     :msg "`images-resize resize image file and set `:width` metadata")
        (value-check :path "public/test-image_100.jpg"
                     :value-fn #(meta= %1 %2 :height 100)
                     :msg "`images-resize resize image file and set `:height` metadata")
        (value-check :path "public/test-image_100.jpg"
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize)
                     :msg "`images-resize should add `:io.perun/images-resize` to `:io.perun/trace`")
        (value-check :path "public/test-image_200.jpg"
                     :value-fn #(meta= %1 %2 :width 200)
                     :msg "`images-resize resize image file and set `:width` metadata")
        (value-check :path "public/test-image_200.jpg"
                     :value-fn #(meta= %1 %2 :height 200)
                     :msg "`images-resize resize image file and set `:height` metadata")
        (value-check :path "public/test-image_200.jpg"
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize)
                     :msg "`images-resize should add `:io.perun/images-resize` to `:io.perun/trace`")))

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
          (comp
           (value-check :path "2017-01-01-test.md"
                        :value-fn #(meta= %1 %2 :parsed parsed-md-basic)
                        :msg "`markdown` should set `:parsed` metadata on markdown file")
           (content-check :path "public/2017-01-01-test.html"
                          :content parsed-md-basic
                          :msg "`markdown` should populate HTML file with parsed content")))

        (p/ttr)
        (testing "ttr"
          (value-check :path "public/2017-01-01-test.html"
                       :value-fn #(meta= %1 %2 :ttr 1)
                       :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count)
        (testing "word-count"
          (value-check :path "public/2017-01-01-test.html"
                       :value-fn #(meta= %1 %2 :word-count 19)
                       :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email :target-key :gravatar)
        (testing "gravatar"
          (value-check :path "public/2017-01-01-test.html"
                       :value-fn
                       #(meta= %1 %2 :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                       :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date)
        (testing "build-date"
          (key-check :path "public/2017-01-01-test.html"
                     :key :date-build
                     :msg "`build-date` should set `:date-build` metadata"))

        (p/slug)
        (testing "slug"
          (value-check :path "public/test.html"
                       :value-fn #(meta= %1 %2 :slug "test")
                       :msg "`slug` should move a file"))

        (p/permalink)
        (testing "permalink"
          (value-check :path "public/test/index.html"
                       :value-fn #(meta= %1 %2 :permalink "/test/")
                       :msg "`permalink` should move a file"))
        (testing "canonical-url"
          (value-check :path "public/test/index.html"
                       :value-fn #(meta= %1 %2 :canonical-url "http://example.com/test/")
                       :msg "`:canonical-url` should be implicitly set"))

        (p/mime-type)
        (testing "mime-type"
          (value-check :path "public/test/index.html"
                       :value-fn #(and (meta= %1 %2 :mime-type "text/html")
                                       (meta= %1 %2 :file-type "text"))
                       :msg "`mime-type` should be set `:mime-type` and `:file-type` metadata"))

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
        (testing "render"
          (content-check :path "public/test/index.html"
                         :content "<body>"
                         :msg "`render` should modify a page"))

        (add-txt-file :path "test.js" :content js-content)
        (p/inject-scripts :scripts #{"test.js"})
        (testing "inject-scripts"
          (content-check :path "public/test/index.html"
                         :content (str "<script>" js-content "</script>")
                         :msg "`inject-scripts` should alter the contents of a file"))

        (p/draft)
        (testing "draft"
          (file-exists? :path "public/test/index.html"
                        :negate? true
                        :msg "`draft` should remove files"))))

(deftesttask with-arguments-test []
  (comp (add-txt-file :path "test.md" :content md-content)
        (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"
                                       :doc-root "hammock"}))
        (p/markdown :out-dir "hammock"
                    :meta {:markdown-set :metadata}
                    :options {:extensions {:smarts true}})
        (testing "markdown"
          (comp
           (value-check :path "test.md"
                        :value-fn #(meta= %1 %2 :parsed parsed-md-smarts)
                        :msg "`markdown` should set `:parsed` metadata on markdown file")
           (content-check :path "hammock/test.html"
                          :content parsed-md-smarts
                          :msg "`markdown` should populate HTML file with parsed content")))
        (sift :move {#"hammock/test\.html" "hammock/test.htm"})

        (p/ttr :filterer :markdown-set
               :extensions [".htm"])
        (testing "ttr"
          (value-check :path "hammock/test.htm"
                       :value-fn #(meta= %1 %2 :ttr 1)
                       :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count :filterer :markdown-set
                      :extensions [".htm"])
        (testing "word-count"
          (value-check :path "hammock/test.htm"
                       :value-fn #(meta= %1 %2 :word-count 19)
                       :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email
                    :target-key :gravatar
                    :filterer :markdown-set
                    :extensions [".htm"])
        (testing "gravatar"
          (value-check :path "hammock/test.htm"
                       :value-fn
                       #(meta= %1 %2 :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                       :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date :filterer :markdown-set
                      :extensions [".htm"])
        (testing "build-date"
          (key-check :path "hammock/test.htm"
                     :key :date-build
                     :msg "`build-date` should set `:date-build` metadata"))

        (p/slug :filterer :markdown-set
                :extensions [".htm"]
                :slug-fn (fn [_ _] "time"))
        (testing "slug"
          (value-check :path "hammock/time.htm"
                       :value-fn #(meta= %1 %2 :slug "time")
                       :msg "`:slug` should move a file"))

        (p/permalink :filterer :markdown-set
                     :extensions [".htm"]
                     :permalink-fn (fn [_ _] "/foo.htm"))
        (testing "permalink"
          (value-check :path "hammock/foo.htm"
                       :value-fn #(meta= %1 %2 :permalink "/foo.htm")
                       :msg "`permalink` should move a file"))
        (testing "canonical-url"
          (value-check :path "hammock/foo.htm"
                       :value-fn #(meta= %1 %2 :canonical-url "http://example.com/foo.htm")
                       :msg "`canonical-url` should be implicitly set"))

        (p/mime-type :filterer :markdown-set
                     :extensions [".htm"])
        (testing "mime-type"
          (value-check :path "hammock/foo.htm"
                       :value-fn #(and (meta= %1 %2 :mime-type "text/html")
                                       (meta= %1 %2 :file-type "text"))
                       :msg "`mime-type` should be set `:mime-type` and `:file-type` metadata"))

        (p/sitemap :filterer :markdown-set
                   :extensions [".htm"]
                   :filename "test.xml"
                   :out-dir "foo"
                   :url "http://bar.com/")
        (testing "sitemap"
          (file-exists? :path "foo/test.xml"
                        :msg "`sitemap` should write test.xml"))

        (p/rss :filterer :markdown-set
               :extensions [".htm"]
               :filename "test.rss"
               :out-dir "foo"
               :base-url "http://bar.com/"
               :site-title "Test Site"
               :description "Here we go a-testing")
        (testing "rss"
          (file-exists? :path "foo/test.rss"
                        :msg "`rss` should write test.rss"))

        (p/atom-feed :filterer :markdown-set
                     :extensions [".htm"]
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
                  :extensions [".htm"]
                  :out-dir "bar"
                  :meta {:set-by-render true})
        (testing "render"
          (comp
           (content-check :path "bar/hammock/foo.htm"
                          :content "<body>"
                          :msg "`render` should modify a page")
           (value-check :path "bar/hammock/foo.htm"
                        :value-fn #(meta= %1 %2 :set-by-render true)
                        :msg "`render` should set metadata")))

        (add-txt-file :path "test.js" :content js-content)
        (add-txt-file :path "baz.htm" :content "<body></body>")
        (p/inject-scripts :scripts #{"test.js"} :filter #{#"foo"} :extensions [".htm"])
        (p/inject-scripts :scripts #{"test.js"} :remove #{#"baz"} :extensions [".htm"])
        (testing "inject-scripts"
          (comp
           (content-check :path "bar/hammock/foo.htm"
                          :content (str "<script>" js-content "</script>")
                          :msg "`inject-scripts` should alter the contents of a file")
           (content-check :path "baz.htm"
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
          (content-check :path "public/test.html"
                         :content "Salutations"
                         :msg "content changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: false"))
        (p/markdown)
        (testing "detecting metadata changes"
          (value-check :path "public/test.html"
                       :value-fn #(meta= %1 %2 :draft false)
                       :msg "metadata changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (str/replace md-content #"draft: true" "draft: true\nfoo: bar"))
        (p/markdown)
        (testing "detecting metadata additions"
          (value-check :path "public/test.html"
                       :value-fn #(meta= %1 %2 :foo "bar")
                       :msg "metadata additions should result in re-rendering"))

        (add-txt-file :path "test.md" :content md-content)
        (p/markdown)
        (testing "detecting metadata deletions"
          (value-check :path "public/test.html"
                       :value-fn #(meta= %1 %2 :foo nil)
                       :msg "metadata deletions should result in re-rendering"))

        (add-txt-file :path "test2.md" :content md-content)
        (p/markdown)
        (testing "detecting new files"
          (comp
           (content-check :path "public/test2.html"
                          :content parsed-md-basic
                          :msg "new files should be parsed, after initial render")
           (value-check :path "test2.md"
                        :value-fn #(meta= %1 %2 :parsed parsed-md-basic)
                        :msg "new files should have `:parsed` set on them, after initial render")))))
