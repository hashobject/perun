(ns io.perun.contrib.asciidoctor-test
  (:require [clojure.test :refer :all]
            [io.perun.contrib.asciidoctor :refer :all]))

(def sample-adoc "
---
draft:
name: in my own image
---
= In my own image: Perun
:author: Zeus zeus@thunderdome.olympus
:revdate: 02-08-907
:toc:

I Zeus would like to describe how the god Perun relates to my image.

[quote, Perun, Having struck Veles]
\"Well, there is your place, remain there!\"

.No power more godlike then the Clojure power of Perun
[source, clojure]
----
(deftask build
  \"Build blog.\"
  []
  (comp (asciidoctor)
        (render :renderer renderer)))
----
")

(def expected-html "<div class=\"paragraph\">
<p>I Zeus would like to describe how the god Perun relates to my image.</p>
</div>
<div class=\"quoteblock\">
<blockquote>
\"Well, there is your place, remain there!\"
</blockquote>
<div class=\"attribution\">
&#8212; Perun<br>
<cite>Having struck Veles</cite>
</div>
</div>
<div class=\"listingblock\">
<div class=\"title\">No power more godlike then the Clojure power of Perun</div>
<div class=\"content\">
<pre class=\"highlight\"><code class=\"language-clojure\" data-lang=\"clojure\">(deftask build
  \"Build blog.\"
  []
  (comp (asciidoctor)
        (render :renderer renderer)))</code></pre>
</div>
</div>")

(def expected-meta
  {:draft nil
   :name "in my own image"
   :original true})

(def +asciidoctor-defaults+
  {:gempath    ""
   :libraries  '("asciidoctor-diagram")
   :attributes {:generator "perun"}})

(deftest test-asciidoc-to-html
  "Test the `asciidoc-to-html` function on its actual conversion."
  (let [rendered (asciidoc-to-html sample-adoc (normalize-options +asciidoctor-defaults+))]
    (is (= expected-html rendered))))

(deftest test-parse-file-metadata
  "Test the metadata extraction by `parse-file-metadata`."
  (let [metadata (parse-file-metadata sample-adoc)]
    (is (= expected-meta metadata))))
