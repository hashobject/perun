![perun-logo-big](perun.png)

[![Clojars Project](https://img.shields.io/clojars/v/perun.svg)](https://clojars.org/perun)
[![Dependencies Status](http://jarkeeper.com/hashobject/perun/status.svg)](http://jarkeeper.com/hashobject/perun)
[![Downloads](https://jarkeeper.com/hashobject/perun/downloads.svg)](https://jarkeeper.com/hashobject/perun)

Simple, composable static site generator built on top of the [Boot](http://boot-clj.com/).
Inspired by Boot task model and [Metalsmith](http://www.metalsmith.io/).
Perun is a collection of boot tasks that you can chain together and build something custom
that suits your needs. Please checkout out [Getting started](https://github.com/hashobject/perun/wiki/Getting-Started) guide.

## For information and help

[Clojurians slack](https://clojurians.slack.com/) ([join](http://clojurians.net/))
has a channel [#perun](https://clojurians.slack.com/messages/perun/) for talk about Perun.

Check [SPEC.md](./SPEC.md) for documentation about metadata keys used by built-in tasks.


## Plugins

See the [Built-Ins Guide](https://perun.io/guides/built-ins/) for a list of built-in tasks.


## 3rd party useful plugins

There are plenty of Boot plugins that can be useful in the when you are using perun:

 - [boot-http](https://github.com/pandeiro/boot-http) - serve generated site locally using web server
 - [boot-gzip](https://github.com/martinklepsch/boot-gzip) - gzip files
 - [boot-s3](https://github.com/hashobject/boot-s3) - sync generated site to the Amazon S3
 - [boot-less](https://github.com/Deraen/boot-less) - task to compile Less to CSS
 - [boot-sassc](https://github.com/mathias/boot-sassc) - task to compile Sass to CSS
 - [boot-garden](https://github.com/martinklepsch/boot-garden) - task to compile Garden stylesheets to CSS
 - [boot-autoprefixer](https://github.com/danielsz/boot-autoprefixer) - add vendor prefixes to your CSS
 - [boot-reload](https://github.com/adzerk-oss/boot-reload) - live-reload of browser Cljs, HTML, CSS and images (Requires Cljs).
 - [boot-livereload](https://github.com/deraen/boot-livereload) - live-reload of browser JS, HTML, CSS and images.
 - [boot-hyphenate](https://github.com/deraen/boot-hyphenate) - hyphenate HTML files with soft-hyphens.

## Version

We use Clojure 1.7.0 and Boot 2.7.2. You should have those versions in order to use perun.

## Plugins system

Everything in perun is build like independent task. The simplest blog engine will look like:

```clojure
(deftask build
  "Build blog."
  []
  (comp (markdown)
        (render :renderer renderer)))

```

But if you want to make permalinks, generate sitemap and rss feed, hide unfinished posts, add time to read to each post then you will do:

```clojure
(deftask build
  "Build blog."
  []
  (comp (markdown)
        (draft)
        (ttr)
        (slug)
        (permalink)
        (render :renderer renderer)
        (sitemap :filename "sitemap.xml")
        (rss :site-title "Hashobject" :description "Hashobject blog" :base-url "http://blog.hashobject.com/")
        (atom-feed :site-title "Hashobject" :description "Hashobject blog" :base-url "http://blog.hashobject.com/")
        (notify)))
```
You can also chain this with standard boot tasks. E.x. if you want to upload generated files to Amazon S3 you might use
[boot-s3](https://github.com/hashobject/boot-s3) plugin.

Then your code might look like this:
```clojure
(deftask build
  "Build blog."
  []
  (comp (markdown)
        (render :renderer renderer)
        (s3-sync)))
```

## Use cases

 - Generate blog from markdown files.
 - Generate documentation for your open source library based on README.
 - Any case where you'd want to use Jekyll or another static site generator.

## Examples

[A minimal blog example](/example-blog/), included in this repo. See [build.boot](/example-blog/build.boot)

Real-world websites created with perun:

 - [perun.io](https://perun.io). See [build.boot](https://github.com/hashobject/perun.io/blob/master/build.boot)
 - [blog.hashobject.com](http://blog.hashobject.com). See [build.boot](https://github.com/hashobject/blog.hashobject.com/blob/master/build.boot)
 - [code.hashobject.com](http://code.hashobject.com). See [build.boot](https://github.com/hashobject/code.hashobject.com/blob/master/build.boot)
 - [deraen.github.io](http://deraen.github.io/). See [build.boot](https://github.com/Deraen/deraen.github.io/blob/blog/build.boot)
 - [eccentric-j.com](https://eccentric-j.com). See [build.boot](https://github.com/eccentric-j/idle-parens/blob/master/build.boot)
 - [www.martinklepsch.org](http://www.martinklepsch.org/). See [build.boot](https://github.com/martinklepsch/martinklepsch.org/blob/master/build.boot)
 - [presumably.de](https://presumably.de/). See [build.boot](https://github.com/pesterhazy/presumably/blob/master/build.boot)
 - [nicerthantriton.com](https://nicerthantriton.com/). See [build.boot](https://github.com/bhagany/nicerthantriton.com/blob/master/build.boot)
 - [200ok.ch](http://200ok.ch). See [build.boot](https://gitlab.200ok.ch/200ok/200ok.ch/blob/master/build.boot)
 - [sooheon.org](http://sooheon.org). See [build.boot](https://github.com/sooheon/sooheon.org/blob/master/build.boot)
 - [ballpointcarrot.net](http://ballpointcarrot.net). See [build.boot](https://github.com/ballpointcarrot/ballpointcarrot.github.io/blob/master/.perun/build.boot)
 - [jstaffans.github.io](https://jstaffans.github.io). See [build.boot](https://github.com/jstaffans/jstaffans.github.io/blob/static-perun/build.boot)
 - [www.clojurebridgemn.org](http://www.clojurebridgemn.org/). See [build.boot](https://gitlab.com/clojurebridge-mn/clojurebridgemn.org/blob/master/build.boot)


## How does it work

Perun works in the following steps:

  1. Read all the files from the source directory and create fileset metadata `(:metadata (meta fileset)` with all meta information available for all tasks/plugins
  2. Call each perun task/plugin to manipulate the fileset metadata
  3. Write the results to the destination/target directory

Perun embraces Boot task model. Fileset is the main abstraction and the most important thing you should care about.
When you use perun you need to create custom task that is a composition of standard and 3d party tasks/plugins/functions.
Perun takes set of files as input
(e.x. source markdown files for your blog)
and produces another set of files as output
(e.x. generated deployable html for your blog).

Fileset passed to every task has metadata `(:metadata (meta fileset)`.
This metadata contains accumulated information from each task.
More info about structure of this metadata is coming.

## Install

```clojure
[perun "0.3.0"]
```

## Usage

Create `build.boot` file with similar content. For each task please specify your own options.
See documentation for each task to find all supported options for each plugin.

```clojure
(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.7.0"]
                  [hiccup "1.0.5"]
                  [perun "0.2.0-SNAPSHOT"]
                  [clj-time "0.9.0"]
                  [hashobject/boot-s3 "0.1.2-SNAPSHOT"]
                  [jeluard/boot-notify "0.1.2" :scope "test"]])

(task-options!
  pom {:project 'blog.hashobject.com
       :version "0.2.0"}
  s3-sync {
    :bucket "blog.hashobject.com"
    :source "resources/public/"
    :access-key (System/getenv "AWS_ACCESS_KEY")
    :secret-key (System/getenv "AWS_SECRET_KEY")
    :options {"Cache-Control" "max-age=315360000, no-transform, public"}})

(require '[io.perun :refer :all])
(require '[hashobject.boot-s3 :refer :all])
(require '[jeluard.boot-notify :refer [notify]])

(defn renderer [{global :meta posts :entries post :entry}] (:name post))

(defn index-renderer [{global :meta files :entries}]
  (let [names (map :title files)]
    (clojure.string/join "\n" names)))

(deftask build
  "Build blog."
  []
  (comp (markdown)
        (draft)
        (ttr)
        (slug)
        (permalink)
        (render :renderer renderer)
        (collection :renderer index-renderer :page "index.html")
        (sitemap :filename "sitemap.xml")
        (rss :site-title "Hashobject" :description "Hashobject blog" :base-url "http://blog.hashobject.com/")
        (s3-sync)
        (notify)))
```

After you created `build` task simply do:

```bash
boot build
```

## Tips

### Debug

To see more detailed output from each task (useful for debugging) please use
`--verbose` boot flag. E.x. `boot --verbose dev`

### Development setup

Perun is static site generator. So usually you'd use it by just running `boot build` which will generate your static site.
This process is robust and great for production but it's slow and lacks fast feedback when you're developing your site locally.
In order to solve this problem we recommend following setup:

1. Have 2 separate tasks for building local version and production version. E.x. `build-dev` and `build`.
2. Include [boot-http](https://github.com/pandeiro/boot-http) into your `build.boot` file. This will enable serving your site using web server.
3. Create task `dev` that will call `build-dev` on any change to your source files:

```clojure
  (deftask dev
    []
    (comp (watch)
          (build-dev)
          (serve :resource-root "public")))
```
4. Run`boot dev`
In such setup you will have HTTP web server serving your generated content that would be regenerated every time you change
your source files. So you'd be able to preview your changes almost immediately.


### Auto deployment

It's quite easy to setup automatic static site deployment.
E.x. you have GitHub repo for your blog and you are using `boot-s3` to sync files to Amazon S3.
In this case it's possible to setup flow in a way that every commit to GitHub would be built on Heroku using perun and deployed to AWS S3.

Assuming you have setup similar to [example](https://github.com/hashobject/blog.hashobject.com/blob/master/build.boot#L31) in order to achieve this you need to:
 - create [Heroku](heroku.com) application for your GitHub repo with `build.boot` file
 - ensure that `build.boot` has `build` task that has tasks build and deploy tasks
 - specify `AWS_ACCESS_KEY` and `AWS_SECRET_KEY` envs. They are mandatory for the `boot-s3` plugin).
 - add boot/perun buildpack `heroku buildpacks:add https://github.com/hashobject/heroku-buildpack-perun`
 - enable GitHub integration https://devcenter.heroku.com/articles/github-integration
 - change your site in GitHub and see changes deployed to AWS S3 in few minutes

Similar auto deployment can be configured using [CircleCI](http://circleci.com) too.

## Contributions

We love contributions. Please submit your pull requests.

## Main Contributors

- [Martin Klepsch](https://github.com/martinklepsch)
- [Juho Teperi](https://github.com/Deraen)
- [Brent Hagany](https://github.com/bhagany)

## Copyright and License

Copyright © 2013-2019 Hashobject Ltd (team@hashobject.com) and [perun Contributors](https://github.com/hashobject/perun/graphs/contributors).

Distributed under the [Eclipse Public License](http://opensource.org/licenses/eclipse-1.0).
