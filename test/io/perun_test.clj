(ns io.perun-test
  (:require [boot.core :as boot :refer [deftask]]
            [boot.task.built-in :refer [sift]]
            [boot.test :as boot-test :refer [deftesttask]]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [io.perun :as p]
            [io.perun.core :as perun]
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

(deftask prn-content
  [p path PATH str "path of the file to print"]
  (boot/with-pass-thru fileset
    (->> path (boot/tmp-get fileset) boot/tmp-file slurp prn)))

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
          buffered-image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
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
  (comp (add-image :path "public/test-image-1.jpg" :type "JPG" :width 10 :height 10)
        (add-image :path "public/images/test-image-2.jpg" :type "JPG" :width 10 :height 10)
        (p/images-resize :resolutions #{100 200})
        (value-check :path (perun/url-to-path "test-image-1_100.jpg")
                     :value-fn #(meta= %1 %2 :width 100)
                     :msg "`images-resize resize image file and set `:width` metadata")
        (value-check :path (perun/url-to-path "test-image-1_100.jpg")
                     :value-fn #(meta= %1 %2 :height 100)
                     :msg "`images-resize resize image file and set `:height` metadata")
        (value-check :path (perun/url-to-path "test-image-1_100.jpg")
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize)
                     :msg "`images-resize should add `:io.perun/images-resize` to `:io.perun/trace`")
        (value-check :path (perun/url-to-path "test-image-1_200.jpg")
                     :value-fn #(meta= %1 %2 :width 200)
                     :msg "`images-resize resize image file and set `:width` metadata")
        (value-check :path (perun/url-to-path "test-image-1_200.jpg")
                     :value-fn #(meta= %1 %2 :height 200)
                     :msg "`images-resize resize image file and set `:height` metadata")
        (value-check :path (perun/url-to-path "test-image-1_200.jpg")
                     :value-fn #(meta-contains? %1 %2 :io.perun/trace :io.perun/images-resize)
                     :msg "`images-resize should add `:io.perun/images-resize` to `:io.perun/trace`")
        (value-check :path (perun/url-to-path "public/images/test-image-2_100.jpg")
                     :value-fn #(meta= %1 %2 :width 100)
                     :msg "`images-resize resize image file and set `:width` metadata")
        (add-image :path "public/images/test-image-3.jpg" :type "JPG" :width 10 :height 10)
        (p/images-resize :resolutions #{100} :out-dir "public")
        (value-check :path (perun/url-to-path "public/test-image-3_100.jpg")
                     :value-fn #(meta= %1 %2 :width 100)
                     :msg "`images-resize resize image file and set `:width` metadata")))

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

(def base-meta {:email "brent.hagany@gmail.com"
                :author "Testy McTesterson"})
(def yamls [(yaml/generate-string (assoc base-meta
                                         :uuid "2078a34d-1b1a-4257-9eff-ffe215d90bcd"
                                         :draft true))
            (yaml/generate-string (assoc base-meta
                                         :uuid "2078a34d-1b1a-4257-9eff-ffe215d90bcd"
                                         :draft false))
            (yaml/generate-string (assoc base-meta
                                         :uuid "2078a34d-1b1a-4257-9eff-ffe215d90bcd"
                                         :draft true
                                         :order 4
                                         :foo "bar"))

            (yaml/generate-string (assoc base-meta
                                         :uuid "e98ae98f-a621-47f3-a4be-de8b06961f41"
                                         :tags ["tag1" "tag2" "tag3"]
                                         :order 3
                                         :baz true))
            (yaml/generate-string (assoc base-meta
                                         :uuid "2d4f8006-4a3b-4099-9f7b-3b8c9349a3dc"
                                         :tags ["tag1" "tag2"]
                                         :order 2
                                         :baz false))
            (yaml/generate-string (assoc base-meta
                                         ;; :uuid
                                         :tags ["tag1" "tag3"]
                                         :order 1
                                         :baz false))
            (yaml/generate-string (assoc base-meta
                                         ;; :uuid
                                         :tags ["tag2" "tag3"]
                                         :order 0
                                         :baz true))])

(def md-content
  "# Hello there

This --- be ___markdown___.")

(def adoc-content
  "= Hello there

This --- be _asciidoc_.")

(defn meta-block
  [meta]
  (str "---\n" meta "\n---\n"))

(def input-strings (map #(str (meta-block %) md-content) yamls))

(def adoc-input-strings (map #(str (meta-block %) adoc-content) yamls))

(def highlight-input-md-string
  (str
   (meta-block (yaml/generate-string (assoc base-meta :uuid "f948c938-3cf2-4feb-bf02-f284c2fe9665")))
   "```scss
@import 'bootstrap';
```"))

(def highlight-input-ad-string
  (str
   (meta-block (yaml/generate-string (assoc base-meta :uuid "ce14e4fc-8c65-4e26-94fe-55faaa650c01")))
   "[source,scss]
----
@import 'bootstrap';
----"))

(def parsed-md-basic "<h1><a href=\"#hello-there\" id=\"hello-there\"></a>Hello there</h1>\n<p>This --- be <strong><em>markdown</em></strong>.</p>\n")

(def parsed-pandoc-basic "<h1 id=\"hello-there\">Hello there</h1>\n<p>This — be <strong><em>markdown</em></strong>.</p>\n")

(def parsed-asciidoctor-md "<div class=\"paragraph\">\n<p>This --- be <em><em>markdown</em></em>.</p>\n</div>")

(def parsed-asciidoctor-adoc "<div class=\"paragraph\">\n<p>This --- be <em>asciidoc</em>.</p>\n</div>")

(def parsed-md-smarts "<h1><a href=\"#hello-there\" id=\"hello-there\"></a>Hello there</h1>\n<p>This &mdash; be <strong><em>markdown</em></strong>.</p>\n")

(def highlighted-md "<pre><code class=\"highlight\"><span></span><span class=\"k\">@import</span> <span class=\"s1\">'</span><span class=\"s2\">bootstrap'</span><span class=\"p\">;</span>\n</code></pre>")

(def highlighted-ad "<pre class=\"highlight\"><code class=\"highlight\" data-lang=\"scss\"><span></span><span class=\"k\">@import</span> <span class=\"s1\">'</span><span class=\"s2\">bootstrap'</span><span class=\"p\">;</span>\n</code></pre>")

(def js-content "(function somejs() { console.log('$foo'); })();")

(def less-content "@base: #f938ab;

.box-shadow(@style, @c) when (iscolor(@c)) {
  -webkit-box-shadow: @style @c;
  box-shadow:         @style @c;
}
.box-shadow(@style, @alpha: 50%) when (isnumber(@alpha)) {
  .box-shadow(@style, rgba(0, 0, 0, @alpha));
}
.box {
  color: saturate(@base, 5%);
  border-color: lighten(@base, 30%);
  div { .box-shadow(0 0 5px, 30%) }
}")

(def import-less "@import \"content\";")

(def parsed-less ".box {
  color: #fe33ac;
  border-color: #fdcdea;
}
.box div {
  -webkit-box-shadow: 0 0 5px rgba(0, 0, 0, 0.3);
  box-shadow: 0 0 5px rgba(0, 0, 0, 0.3);
}")

(deftesttask global-metadata-test []
  (comp (add-txt-file :path "perun.base.edn" :content "{:global \"metadata!\"}")
        (p/global-metadata)
        (boot/with-pass-thru fileset
          (is (= (:global (pm/+global-meta-key+ (meta fileset))) "metadata!")
              "global metadata should be set from perun.base.edn"))))

(defn render
  [data]
  (str "<body>" (:content (:entry data)) "</body>"))

(defn render-assortment
  [data]
  (let [{:keys [entry entries]} data]
    (str "<h1>assortment " (count entries) "</h1>")))

(defn render-collection
  [data]
  (let [{:keys [entry entries]} data]
    (str "<h1>collection " (count entries) "</h1>")))

(defn render-tags
  [data]
  (let [{:keys [entry entries]} data]
    (str "<h1>tags " (count entries) "</h1>")))

(defn render-paginate
  [data]
  (let [{:keys [entry entries]} data]
    (str "<h1>paginate " (count entries) "</h1>")))

(defn render-static
  [data]
  "<h1>static</h1>")

(deftesttask default-tests []
  (comp (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"}))

        (add-txt-file :path "2017-01-01-test.md" :content (nth input-strings 0))
        (p/pandoc)
        (testing "pandoc"
          (content-check :path "public/2017-01-01-test.html"
                         :content parsed-pandoc-basic
                         :msg "`pandoc` should populate HTML file with parsed content"))

        (add-txt-file :path "2017-01-01-test.adoc" :content (nth input-strings 0))
        (p/asciidoctor)
        (testing "asciidoctor for markdown"
          (content-check :path (perun/url-to-path "public/2017-01-01-test.html")
                         :content parsed-asciidoctor-md
                         :msg "`asciidoctor` should populate HTML file with parsed markdown content"))

        (add-txt-file :path "2017-01-01-test.adoc" :content (nth adoc-input-strings 0))
        (p/asciidoctor)
        (testing "asciidoctor for asciidoc"
          (content-check :path (perun/url-to-path "public/2017-01-01-test.html")
                         :content parsed-asciidoctor-adoc
                         :msg "`asciidoctor` should populate HTML file with parsed asciidoc content"))

        (add-txt-file :path "2017-01-01-test.md" :content (nth input-strings 0))
        (p/markdown)

        (testing "markdown"
          (content-check :path (perun/url-to-path "public/2017-01-01-test.html")
                         :content parsed-md-basic
                         :msg "`markdown` should populate HTML file with parsed content"))

        (add-txt-file :path "highlight-test.md" :content highlight-input-md-string)
        (p/markdown)
        (p/highlight)

        (testing "highlight for markdown"
          (content-check :path (perun/url-to-path "public/highlight-test.html")
                         :content highlighted-md
                         :msg "`highlight` should add Pygments syntax highlighting to HTML file"))

        (add-txt-file :path "highlight-test.ad" :content highlight-input-ad-string)
        (p/asciidoctor)
        (p/highlight)

        (testing "highlight for asciidoc"
          (content-check :path (perun/url-to-path "public/highlight-test.html")
                         :content highlighted-ad
                         :msg "`highlight` should add Pygments syntax highlighting to HTML file"))

        (p/ttr)
        (testing "ttr"
          (value-check :path (perun/url-to-path "public/2017-01-01-test.html")
                       :value-fn #(meta= %1 %2 :ttr 1)
                       :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count)
        (testing "word-count"
          (value-check :path (perun/url-to-path "public/2017-01-01-test.html")
                       :value-fn #(meta= %1 %2 :word-count 5)

                       :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email :target-key :gravatar)
        (testing "gravatar"
          (value-check :path (perun/url-to-path "public/2017-01-01-test.html")
                       :value-fn
                       #(meta= %1 %2 :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                       :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date)
        (testing "build-date"
          (key-check :path (perun/url-to-path "public/2017-01-01-test.html")
                     :key :date-build
                     :msg "`build-date` should set `:date-build` metadata"))

        (p/slug)
        (testing "slug"
          (value-check :path (perun/url-to-path "public/test.html")
                       :value-fn #(meta= %1 %2 :slug "test")
                       :msg "`slug` should move a file"))

        (p/permalink)
        (testing "permalink"
          (value-check :path (perun/url-to-path "public/test/index.html")
                       :value-fn #(meta= %1 %2 :permalink "/test/")
                       :msg "`permalink` should move a file"))
        (testing "canonical-url"
          (value-check :path (perun/url-to-path "public/test/index.html")
                       :value-fn #(meta= %1 %2 :canonical-url "http://example.com/test/")
                       :msg "`:canonical-url` should be implicitly set"))

        (p/mime-type)
        (testing "mime-type"
          (value-check :path (perun/url-to-path "public/test/index.html")
                       :value-fn #(and (meta= %1 %2 :mime-type "text/html")
                                       (meta= %1 %2 :file-type "text"))
                       :msg "`mime-type` should be set `:mime-type` and `:file-type` metadata"))

        (p/sitemap)
        (testing "sitemap"
          (file-exists? :path (perun/url-to-path "public/sitemap.xml")
                        :msg "`sitemap` should write sitemap.xml"))

        (p/rss)
        (testing "rss"
          (file-exists? :path (perun/url-to-path "public/feed.rss")
                        :msg "`rss` should write feed.rss"))

        (p/atom-feed)
        (testing "atom-feed"
          (file-exists? :path (perun/url-to-path "public/atom.xml")
                        :msg "`atom-feed` should write atom.xml"))

        (add-txt-file :path "test2.md" :content (nth input-strings 3))
        (add-txt-file :path "test3.md" :content (nth input-strings 4))
        (add-txt-file :path "test4.md" :content (nth input-strings 5))
        (add-txt-file :path "test5.md" :content (nth input-strings 6))
        (p/markdown)

        (p/assortment :renderer 'io.perun-test/render-assortment)
        (testing "assortment"
          (content-check :path (perun/url-to-path "public/index.html")
                         :content "assortment 6"
                         :msg "assortment should modify file contents"))

        (p/collection :renderer 'io.perun-test/render-collection)
        (testing "collection"
          (content-check :path (perun/url-to-path "public/index.html")
                         :content "collection 7"
                         :msg "collection should modify file contents"))

        (p/tags :renderer 'io.perun-test/render-tags)
        (testing "tags"
          (comp
           (content-check :path (perun/url-to-path "public/tag1.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")
           (content-check :path (perun/url-to-path "public/tag2.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")
           (content-check :path (perun/url-to-path "public/tag3.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")))

        (p/paginate :renderer 'io.perun-test/render-paginate)
        (testing "paginate"
          (content-check :path (perun/url-to-path "public/page-1.html")
                         :content "paginate 10"
                         :msg "`paginate` should write new files"))

        (p/static :renderer 'io.perun-test/render-static)
        (testing "static"
          (content-check :path (perun/url-to-path "public/index.html")
                         :content "<h1>static</h1>"
                         :msg "`static` should write new files"))

        (p/render :renderer 'io.perun-test/render)
        (testing "render"
          (content-check :path (perun/url-to-path "public/test/index.html")
                         :content "<body>"
                         :msg "`render` should modify a page"))

        (add-txt-file :path "test.js" :content js-content)
        (p/inject-scripts :scripts #{"test.js"})
        (testing "inject-scripts"
          (content-check :path (perun/url-to-path "public/test/index.html")
                         :content (str "<script>" js-content "</script>")
                         :msg "`inject-scripts` should alter the contents of a file"))

        (p/draft)
        (testing "draft"
          (file-exists? :path (perun/url-to-path "public/test/index.html")
                        :negate? true
                        :msg "`draft` should remove files"))

        (add-txt-file :path "test.less" :content less-content)
        (p/lessc)
        (testing "lessc"
          (content-check :path (perun/url-to-path "public/test.css")
                         :content parsed-less
                         :msg "`lessc` should parse less into css"))))

(deftesttask with-arguments-test []
  (comp (boot/with-pre-wrap fileset
          (pm/set-global-meta fileset {:base-url "http://example.com/"
                                       :site-title "Test Title"
                                       :description "Test Desc"
                                       :doc-root "hammock"}))

        (add-txt-file :path "test.html" :content parsed-pandoc-basic)
        (p/pandoc :out-dir nil
                  :out-ext ".md"
                  :filterer #(= (:path %) "test.html")
                  :extensions [".html"]
                  :meta {:pandoc-set :metadata}
                  :cmd-opts ["-f" "html" "-t" "markdown"])
        (testing "pandoc"
          (content-check :path "test.md"
                         :content "Hello there\n===========\n\nThis --- be ***markdown***.\n"
                         :msg "`pandoc` should parse HTML to markdown"))

        (add-txt-file :path "test.md" :content (nth input-strings 0))
        (p/asciidoctor :out-dir nil
                       :filterer #(= (:path %) "test.md")
                       :extensions [".md"]
                       :meta {:asciidoctor-set :metadata})
        (testing "asciidoctor for markdown"
          (content-check :path "test.html"
                         :content parsed-asciidoctor-md
                         :msg "`asciidoctor` should populate HTML with parsed markdown content"))

        (add-txt-file :path "test.adoc" :content (nth adoc-input-strings 0))
        (p/asciidoctor :out-dir nil
                       :filterer #(= (:path %) "test.adoc")
                       :extensions [".adoc"]
                       :meta {:asciidoctor-set :metadata})
        (testing "asciidoctor for asciidoc"
          (content-check :path "test.html"
                         :content parsed-asciidoctor-adoc
                         :msg "`asciidoctor` should populate HTML with parsed asciidoc content"))

        (add-txt-file :path "test.md" :content (nth input-strings 0))
        (p/markdown :out-dir "hammock"
                    :filterer #(= (:path %) "test.md")
                    :meta {:markdown-set :metadata}
                    :md-exts {:smarts true})
        (testing "markdown"
          (content-check :path (perun/url-to-path "hammock/test.html")
                         :content parsed-md-smarts
                         :msg "`markdown` should populate HTML file with parsed content"))
        (sift :move {(re-pattern (java.util.regex.Pattern/quote
                                  (perun/url-to-path "hammock/test.html")))
                     (str/replace (perun/url-to-path "hammock/test.htm")
                                  #"([\\\$])"
                                  "\\\\$1")})

        (p/ttr :filterer :markdown-set
               :extensions [".htm"])
        (testing "ttr"
          (value-check :path (perun/url-to-path "hammock/test.htm")
                       :value-fn #(meta= %1 %2 :ttr 1)
                       :msg "`ttr` should set `:ttr` metadata"))

        (p/word-count :filterer :markdown-set
                      :extensions [".htm"])
        (testing "word-count"
          (value-check :path (perun/url-to-path "hammock/test.htm")
                       :value-fn #(meta= %1 %2 :word-count 5)
                       :msg "`word-count` should set `:word-count` metadata"))

        (p/gravatar :source-key :email
                    :target-key :gravatar
                    :filterer :markdown-set
                    :extensions [".htm"])
        (testing "gravatar"
          (value-check :path (perun/url-to-path "hammock/test.htm")
                       :value-fn
                       #(meta= %1 %2 :gravatar "http://www.gravatar.com/avatar/a1a361f6c96acb1e31ad4b3bbf7aa444")
                       :msg "`gravatar` should set `:gravatar` metadata"))

        (p/build-date :filterer :markdown-set
                      :extensions [".htm"])
        (testing "build-date"
          (key-check :path (perun/url-to-path "hammock/test.htm")
                     :key :date-build
                     :msg "`build-date` should set `:date-build` metadata"))

        (p/slug :filterer :markdown-set
                :extensions [".htm"]
                :slug-fn (fn [_ _] "time"))
        (testing "slug"
          (value-check :path (perun/url-to-path "hammock/time.htm")
                       :value-fn #(meta= %1 %2 :slug "time")
                       :msg "`:slug` should move a file"))

        (p/permalink :filterer :markdown-set
                     :extensions [".htm"]
                     :permalink-fn (fn [_ _] "/foo.htm"))
        (testing "permalink"
          (value-check :path (perun/url-to-path "hammock/foo.htm")
                       :value-fn #(meta= %1 %2 :permalink "/foo.htm")
                       :msg "`permalink` should move a file"))
        (testing "canonical-url"
          (value-check :path (perun/url-to-path "hammock/foo.htm")
                       :value-fn #(meta= %1 %2 :canonical-url "http://example.com/foo.htm")
                       :msg "`canonical-url` should be implicitly set"))

        (p/mime-type :filterer :markdown-set
                     :extensions [".htm"])
        (testing "mime-type"
          (value-check :path (perun/url-to-path "hammock/foo.htm")
                       :value-fn #(and (meta= %1 %2 :mime-type "text/html")
                                       (meta= %1 %2 :file-type "text"))
                       :msg "`mime-type` should be set `:mime-type` and `:file-type` metadata"))

        (p/sitemap :filterer :markdown-set
                   :extensions [".htm"]
                   :filename "test.xml"
                   :out-dir "foo"
                   :url "http://bar.com/")
        (testing "sitemap"
          (file-exists? :path (perun/url-to-path "foo/test.xml")
                        :msg "`sitemap` should write test.xml"))

        (p/rss :filterer :markdown-set
               :extensions [".htm"]
               :filename "test.rss"
               :out-dir "foo"
               :base-url "http://bar.com/"
               :site-title "Test Site"
               :description "Here we go a-testing")
        (testing "rss"
          (file-exists? :path (perun/url-to-path "foo/test.rss")
                        :msg "`rss` should write test.rss"))

        (p/atom-feed :filterer :markdown-set
                     :extensions [".htm"]
                     :filename "test-atom.xml"
                     :out-dir "foo"
                     :base-url "http://bar.com/"
                     :site-title "Test Site"
                     :description "Here we go a-testing")
        (testing "atom-feed"
          (comp
           (file-exists? :path (perun/url-to-path "foo/test-atom.xml")
                         :msg "`atom-feed` should write test-atom.xml")
           (content-check :path (perun/url-to-path "foo/test-atom.xml")
                          :content "http://bar.com/")
           (content-check :path (perun/url-to-path "foo/test-atom.xml")
                          :content "Test Site")
           (content-check :path (perun/url-to-path "foo/test-atom.xml")
                          :content "Here we go a-testing")))

        (add-txt-file :path "test1.md" :content (nth input-strings 2))
        (add-txt-file :path "test2.md" :content (nth input-strings 3))
        (add-txt-file :path "test3.md" :content (nth input-strings 4))
        (add-txt-file :path "test4.md" :content (nth input-strings 5))
        (add-txt-file :path "test5.md" :content (nth input-strings 6))
        (p/markdown :meta {:assorting true}
                    :out-dir "assorting")
        (sift :move {(re-pattern (str "assorting" perun/file-separator "(.*)\\.html"))
                     (str/replace (str "assorting" perun/file-separator "$1.htm")
                                  #"([\\])"
                                  "\\\\$1")})

        (p/assortment :renderer 'io.perun-test/render-assortment
                      :out-dir "foo"
                      :grouper (fn [entries]
                                 (reduce (fn [paths {:keys [baz tags] :as entry}]
                                           (let [path (str baz "-" (first tags) ".html")]
                                             (if (and (not (nil? baz)) (seq tags))
                                               (update-in paths [path :entries] conj entry)
                                               paths)))
                                         {}
                                         entries))
                      :filterer :assorting
                      :extensions [".htm"]
                      :sortby :order
                      :comparator #(compare %1 %2)
                      :meta {:assorted "yep"})
        (testing "assortment"
          (comp
           (content-check :path (perun/url-to-path "foo/true-tag1.html")
                          :content "assortment 1"
                          :msg "assortment should modify file contents")
           (content-check :path (perun/url-to-path "foo/true-tag2.html")
                          :content "assortment 1"
                          :msg "assortment should modify file contents")
           (content-check :path (perun/url-to-path "foo/false-tag1.html")
                          :content "assortment 2"
                          :msg "assortment should modify file contents")
           (value-check :path (perun/url-to-path "foo/true-tag1.html")
                        :value-fn #(meta= %1 %2 :assorted "yep")
                        :msg "assortment should modify file metadata")
           (value-check :path (perun/url-to-path "foo/true-tag2.html")
                        :value-fn #(meta= %1 %2 :assorted "yep")
                        :msg "assortment should modify file metadata")
           (value-check :path (perun/url-to-path "foo/false-tag1.html")
                        :value-fn #(meta= %1 %2 :assorted "yep")
                        :msg "assortment should modify file metadata")))

        (p/collection :renderer 'io.perun-test/render-collection
                      :out-dir "bar"
                      :filterer :baz
                      :extensions [".htm"]
                      :sortby :order
                      :comparator #(compare %1 %2)
                      :page "its-a-collection.html"
                      :meta {:collected "uh huh"})
        (testing "collection"
          (comp
           (content-check :path (perun/url-to-path "bar/its-a-collection.html")
                          :content "collection 2"
                          :msg "collection should modify file contents")
           (value-check :path (perun/url-to-path "bar/its-a-collection.html")
                        :value-fn #(meta= %1 %2 :collected "uh huh")
                        :msg "collection should modify file metadata")))

        (p/tags :renderer 'io.perun-test/render-tags
                :out-dir "baz"
                :filterer :assorting
                :extensions [".htm"]
                :sortby :order
                :comparator #(compare %1 %2)
                :meta {:tagged "mmhmm"})
        (testing "tags"
          (comp
           (content-check :path (perun/url-to-path "baz/tag1.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")
           (content-check :path (perun/url-to-path "baz/tag2.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")
           (content-check :path (perun/url-to-path "baz/tag3.html")
                          :content "tags 3"
                          :msg "`tags` should write new files")
           (value-check :path (perun/url-to-path "baz/tag1.html")
                        :value-fn #(meta= %1 %2 :tagged "mmhmm"))
           (value-check :path (perun/url-to-path "baz/tag2.html")
                        :value-fn #(meta= %1 %2 :tagged "mmhmm"))
           (value-check :path (perun/url-to-path "baz/tag3.html")
                        :value-fn #(meta= %1 %2 :tagged "mmhmm"))))

        (p/paginate :renderer 'io.perun-test/render-paginate
                    :out-dir "baz"
                    :out-ext ".html"
                    :slug-fn #(str "decomplect-" %)
                    :page-size 2
                    :filterer :assorting
                    :extensions [".htm"]
                    :sortby :order
                    :comparator #(compare %1 %2)
                    :meta {:paginated "mmhmm"})
        (testing "paginate"
          (comp
           (content-check :path (perun/url-to-path "baz/decomplect-1.html")
                          :content "paginate 2"
                          :msg "`paginate` should write new files")
           (content-check :path (perun/url-to-path "baz/decomplect-2.html")
                          :content "paginate 2"
                          :msg "`paginate` should write new files")
           (content-check :path (perun/url-to-path "baz/decomplect-3.html")
                          :content "paginate 1"
                          :msg "`paginate` should write new files")
           (value-check :path (perun/url-to-path "baz/decomplect-1.html")
                        :value-fn #(meta= %1 %2 :paginated "mmhmm")
                        :msg "`paginate` should set metadata")
           (value-check :path (perun/url-to-path "baz/decomplect-2.html")
                        :value-fn #(meta= %1 %2 :paginated "mmhmm")
                        :msg "`paginate` should set metadata")
           (value-check :path (perun/url-to-path "baz/decomplect-3.html")
                        :value-fn #(meta= %1 %2 :paginated "mmhmm")
                        :msg "`paginate` should set metadata")))

        (p/static :renderer 'io.perun-test/render-static
                  :out-dir "laphroiag"
                  :page "neat.html"
                  :meta {:statique "affirmative"})
        (testing "static"
          (comp
           (content-check :path (perun/url-to-path "laphroiag/neat.html")
                          :content "<h1>static</h1>"
                          :msg "`static` should write new files")
           (value-check :path (perun/url-to-path "laphroiag/neat.html")
                        :value-fn #(meta= %1 %2 :statique "affirmative")
                        :msg "`static` should set metadata")))

        (p/render :renderer 'io.perun-test/render
                  :filterer :markdown-set
                  :extensions [".htm"]
                  :out-dir "bar"
                  :meta {:set-by-render true})
        (testing "render"
          (comp
           (content-check :path (perun/url-to-path "bar/hammock/foo.htm")
                          :content "<body>"
                          :msg "`render` should modify a page")
           (value-check :path (perun/url-to-path "bar/hammock/foo.htm")
                        :value-fn #(meta= %1 %2 :set-by-render true)
                        :msg "`render` should set metadata")))

        (add-txt-file :path "test.js" :content js-content)
        (add-txt-file :path "baz.htm" :content "<body></body>")
        (p/inject-scripts :scripts #{"test.js"} :filter #{#"foo"} :extensions [".htm"])
        (p/inject-scripts :scripts #{"test.js"} :remove #{#"baz"} :extensions [".htm"])
        (testing "inject-scripts"
          (comp
           (content-check :path (perun/url-to-path "bar/hammock/foo.htm")
                          :content (str "<script>" js-content "</script>")
                          :msg "`inject-scripts` should alter the contents of a file")
           (content-check :path "baz.htm"
                          :content (str "<script>" js-content "</script>")
                          :negate? true
                          :msg "`inject-scripts` should not alter the contents of a removed file")))

        (add-txt-file :path "test.lss" :content import-less)
        (add-txt-file :path "import-dir/content.less" :content less-content)
        (p/lessc :out-dir "foo"
                 :out-ext ".csss"
                 :filterer #(= (:path %) "test.lss")
                 :extensions [".lss"]
                 :include-dirs ["import-dir"])
        (testing "lessc"
          (content-check :path (perun/url-to-path "foo/test.csss")
                         :content parsed-less
                         :msg "`lessc` should parse less into css"))))

(deftesttask content-tests []
  (comp (testing "Collection works without input files" ;; #77
          (p/collection :renderer 'io.perun-test/render))

        (add-txt-file :path "test.md" :content (nth input-strings 0))
        (p/markdown) ;; render once

        (add-txt-file :path "test.md" :content (str/replace (nth input-strings 0) #"Hello" "Salutations"))
        (p/markdown)
        (testing "detecting content changes"
          (content-check :path (perun/url-to-path "public/test.html")
                         :content "Salutations"
                         :msg "content changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (nth input-strings 1))
        (p/markdown)
        (testing "detecting metadata changes"
          (value-check :path (perun/url-to-path "public/test.html")
                       :value-fn #(meta= %1 %2 :draft false)
                       :msg "metadata changes should result in re-rendering"))

        (add-txt-file :path "test.md" :content (nth input-strings 2))
        (p/markdown)
        (testing "detecting metadata additions"
          (value-check :path (perun/url-to-path "public/test.html")
                       :value-fn #(meta= %1 %2 :foo "bar")
                       :msg "metadata additions should result in re-rendering"))

        (add-txt-file :path "test.md" :content (nth input-strings 0))
        (p/markdown)
        (testing "detecting metadata deletions"
          (value-check :path (perun/url-to-path "public/test.html")
                       :value-fn #(meta= %1 %2 :foo nil)
                       :msg "metadata deletions should result in re-rendering"))

        (add-txt-file :path "test2.md" :content (nth input-strings 3))
        (p/markdown)
        (testing "detecting new files"
          (content-check :path (perun/url-to-path "public/test2.html")
                         :content parsed-md-basic
                         :msg "new files should be parsed, after initial render"))))
