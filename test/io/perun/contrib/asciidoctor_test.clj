(ns io.perun.contrib.asciidoctor-test
  (:require [clojure.test :refer :all]
            [clojure.set :as s]
            [io.perun.contrib.asciidoctor :refer :all]
            [io.perun]))

(def sample-adoc "
---
draft:
name: in my own image
---
= In my own image: Perun
:author: Zeus
:email: zeus@thunderdome.olympus
:revdate: 02-08-907
:toc:
:description: Some posts are close to your heart...

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
  {:appendix-caption "Appendix",
   :asciidoctor "",
   :asciidoctor-version "1.5.4",
   :attribute-missing "skip",
   :attribute-undefined "drop-line",
   :author "Zeus",
   :author-email "zeus@thunderdome.olympus",
   :authorcount 1,
   :authorinitials "Z",
   :authors "Zeus",
   :backend "html5",
   :backend-html5 "",
   :backend-html5-doctype-article "",
   :basebackend "html",
   :basebackend-html "",
   :basebackend-html-doctype-article "",
   :caution-caption "Caution",
  ;  :date-build "2016-09-16",
  ;  :date-modified "2016-09-16"
   :description "Some posts are close to your heart..."
  ;  :docdate "2016-09-16",
  ;  :docdatetime "2016-09-16 08:31:58 CEST",
   :docdir "",
  ;  :doctime "08:31:58 CEST",
   :doctitle "In my own image: Perun"
   :doctype "article",
   :doctype-article "",
   :draft nil, ;; from frontmatter
   :email "zeus@thunderdome.olympus"
   :embedded "",
   :example-caption "Example",
   :figure-caption "Figure",
   :filetype "html",
   :filetype-html "",
   :firstname "Zeus"
   :generator "perun",
   :htmlsyntax "html",
   :iconfont-remote "",
   :iconsdir "./images/icons",
   :important-caption "Important",
   :last-update-label "Last updated",
   :linkcss "",
  ;  :localdate "2016-09-16",
  ;  :localdatetime "2016-09-16 08:31:58 CEST",
  ;  :localtime "08:31:58 CEST",
   :manname-title "NAME",
   :max-include-depth 64,
   :name "In my own image: Perun" ;; frontmatter overwritten by doc
   :note-caption "Note",
   :notitle "",
   :original true, ;; from frontmatter
   :outfilesuffix ".html",
   :prewrap "",
   :revdate "02-08-907"
   :safe-mode-level 20,
   :safe-mode-name "secure",
   :safe-mode-secure "",
   :skip-front-matter ""
   :sectids "",
   :stylesdir ".",
   :stylesheet "",
   :table-caption "Table",
   :tip-caption "Tip",
   :toc ""
   :toc-placement "auto",
   :toc-title "Table of Contents",
   :untitled-label "Untitled",
   :user-home ".",
   :version-label "Version",
   :warning-caption "Warning",
   :webfonts ""})

(def diagram-sample "
= The way Perun does

[plantuml, lightning-direction]
....
Branch --|> Velves
....
")

(def expected-diagram-html "<div class=\"imageblock\">
<div class=\"content\">
<img src=\"lightning-direction.png\" alt=\"lightning direction\" width=\"88\" height=\"175\">
</div>
</div>")

(def n-opts (normalize-options @#'io.perun/+asciidoctor-defaults+))
; deref the private defintion var to circument the private-ness

(def container (new-adoc-container n-opts))

(deftest test-asciidoc-to-html
  "Test the `asciidoc-to-html` function on its actual conversion."
  (let [rendered (asciidoc-to-html container sample-adoc n-opts)]
    (is (= expected-html rendered))))

(deftest test-parse-file-metadata
  "Test the metadata extraction by `parse-file-metadata`."
  (let [metadata (parse-file-metadata container sample-adoc n-opts)]
    (is (s/subset? (into #{} expected-meta) (into #{} metadata)))))

(deftest convert-with-asciidoctor-diagram
  "Test the handling by the `asciidoctor-diagram` library for built-in images"
  (let [rendered (asciidoc-to-html container diagram-sample n-opts)]
    (is (= expected-diagram-html rendered))))
