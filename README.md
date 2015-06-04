# perun

Simple, composable static site generator using [Boot](http://boot-clj.com/).
Inspired by Boot task model and [Metalsmith](http://www.metalsmith.io/).
Perun is a collection of boot tasks/plugins that you can chain together and build something custom
that suits your needs.

## Plugins

 - markdown parser
 - collections
 - drafts
 - calculate time to read for each page
 - sitemap
 - rss
 - permalinks
 - support rendering to any format

## Plugins

Everything in Perun is build like independent task. The simplest blog engine will look like:

```
  (deftask build
    "Build blog."
    []
    (comp (markdown)
          (render :renderer renderer)))

```

But if you want to make permalinks, generate sitemap and rss feed, hide unfinished post, add time to read to each post then you will do:

```
  (deftask build
    "Build blog."
    []
    (comp (markdown)
          (draft)
          (ttr)
          (permalink)
          (render :renderer renderer)
          (sitemap :filename "sitemap.xml")
          (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
          (notify)))

```

## Use cases

 - generate blog from markdown files
 - generate documentation for your open source library bases on README.md
 - any case where you'd want to use jekyll or another static site generator

## Example

See generated blog as an example [blog.hashobject.com](https://github.com/hashobject/blog.hashobject.com/blob/master/build.boot).

## How does it work

Perun works in the following steps:

  1. read all the files from the source directory and create `meta.edn` file will all meta information available for all tasks/plugins
  2. call each perun task/plugin to manipulate the files
  3. write the results to the destination directory

Perun embraces Boot task model. Filesystem is the main abstraction and the most important thing you should care about.
When you use Perun you need to create custom task that is a composition of standard and 3d party tasks/plugins/functions. Perun takes set of files as input (e.x. source markdown files for your blog) and produces another set of files as output (e.x. generated deployable html for your blog).

`meta.edn` is a special file that holds that is usually created once by some task (e.x. `markdown`). This file will hold all meta information about each page of your site. Each task/plugin can update `meta.edn` with more information (or deleted some entries from it).
You can create `meta.edn` manually. It's just a Clojure list of maps. Each map hash meta information about each final generated page. There is no defined format for the map. So you are free do define your own schema for the page metadata.
Some task/plugins however might require existence of specific keys.

## Install

```
[perun "0.1.0-SNAPSHOT"]
```

## Usage

Create `build.boot` file with similar content. For each task please specify your own options.
See documentation for each task to find all supported options for each plugin.

```
  (set-env!
    :source-paths #{"src"}
    :resource-paths #{"resources"}
    :dependencies '[[org.clojure/clojure "1.6.0"]
                   [hiccup "1.0.5"]
                   [perun "0.1.0-SNAPSHOT"]
                   [clj-time "0.9.0"]
                   [jeluard/boot-notify "0.1.2" :scope "test"]])

  (task-options!
    pom {:project 'blog.hashobject.com
         :version "0.2.0"})

  (require '[io.perun.markdown :refer :all])
  (require '[io.perun.ttr :refer :all])
  (require '[io.perun.draft :refer :all])
  (require '[io.perun.permalink :refer :all])
  (require '[io.perun.sitemap :refer :all])
  (require '[io.perun.rss :refer :all])
  (require '[io.perun.render :refer :all])
  (require '[io.perun.collection :refer :all])

  (require '[jeluard.boot-notify :refer [notify]])

  (defn renderer [data] (:name data))

  (defn index-renderer [files]
    (let [names (map :name files)]
      (clojure.string/join "\n" names)))

  (deftask build
    "Build blog."
    []
    (comp (markdown)
          (draft)
          (ttr)
          (permalink)
          (render :renderer renderer)
          (collection :renderer index-renderer :page "index.html")
          (sitemap :filename "sitemap.xml")
          (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
          (notify)))
```

After you created `build` task simply do:

```
  boot build
```



## TODO

  - [ ] robots.txt plugin
  - [ ] humans.txt plugin
  - [ ] amazon s3 plugin
  - [ ] github webhooks plugin

## Contributions

We love contributions. Please submit your pull requests.


## License

Copyright © 2013-2015 Hashobject Ltd (team@hashobject.com).

Distributed under the [Eclipse Public License](http://opensource.org/licenses/eclipse-1.0).
