# perun

Simple, composable static site generator using [Boot](http://boot-clj.com/).
Inspired by Boot task model and [Metalsmith](http://www.metalsmith.io/).
Perun is a collection of boot tasks/plugins that you can chain together and build something custom
that suits your needs.

## Plugins

 - markdown - parse mardown files with YAML metadata
 - collections - generate page that takes all posts data as parameter
 - drafts - exclude pages that have `:draft` flag
 - time-to-read - calculate time to read for each page
 - sitemap - generate sitemap for site
 - rss - generate RSS feed
 - slugs - generate slugs based on any property
 - permalinks - create permalinks for each page
 - rendering to any format - flexible rendering

## Plugins

Everything in Perun is build like independent task. The simplest blog engine will look like:

```clojure
(deftask build
  "Build blog."
  []
  (comp (markdown)
        (render :renderer renderer)))

```

But if you want to make permalinks, generate sitemap and rss feed, hide unfinished post, add time to read to each post then you will do:

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
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
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

 - generate blog from markdown files
 - generate documentation for your open source library bases on README.md
 - any case where you'd want to use jekyll or another static site generator

## Example

See generated blog as an example [blog.hashobject.com](https://github.com/hashobject/blog.hashobject.com/blob/master/build.boot).

## How does it work

Perun works in the following steps:

  1. read all the files from the source directory and create fileset metadata `(:metadata (meta fileset)` with all meta information available for all tasks/plugins
  2. call each perun task/plugin to manipulate the fileset metadata
  3. write the results to the destination directory

Perun embraces Boot task model. Fileset is the main abstraction and the most important thing you should care about.
When you use Perun you need to create custom task that is a composition of standard and 3d party tasks/plugins/functions. Perun takes set of files as input (e.x. source markdown files for your blog) and produces another set of files as output (e.x. generated deployable html for your blog).

Fileset passed to every task has metadata `(:metadata (meta fileset)`. This metadata contains accumulated information from each task. More info about structure of this metadata is coming.

## Install

```clojure
[perun "0.1.3-SNAPSHOT"]
```

## Usage

Create `build.boot` file with similar content. For each task please specify your own options.
See documentation for each task to find all supported options for each plugin.

```clojure
(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [hiccup "1.0.5"]
                  [perun "0.1.0-SNAPSHOT"]
                  [clj-time "0.9.0"]
                  [hashobject/boot-s3 "0.1.0-SNAPSHOT"]
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
        (slug)
        (permalink)
        (render :renderer renderer)
        (collection :renderer index-renderer :page "index.html")
        (sitemap :filename "sitemap.xml")
        (rss :title "Hashobject" :description "Hashobject blog" :link "http://blog.hashobject.com")
        (s3-sync)
        (notify)))
```

After you created `build` task simply do:

```
  boot build
```

## Useful plugins

There are plenty of Boot plugins that can be useful in the when you are using perun:

 - [boot-http](https://github.com/pandeiro/boot-http) - serve generated site locally using web server
 - [boot-gzip](https://github.com/martinklepsch/boot-gzip) - gzip files
 - [boot-s3](https://github.com/hashobject/boot-s3) - sync generated site to the Amazon S3
 - [boot-less](https://github.com/Deraen/boot-less) - task to compile Less to CSS
 - [boot-sassc](https://github.com/mathias/boot-sassc) - task to compile Sass to CSS
 - [boot-garden](https://github.com/martinklepsch/boot-garden) - task to compile Garden stylesheets to CSS
 - [boot-autoprefixer](https://github.com/danielsz/boot-autoprefixer) - add vendor prefixes to your CSS
 - [boot-reload](https://github.com/adzerk-oss/boot-reload) - live-reload of browser css, images, etc.

## Tips

### Dev setup

Perun is static site generator. So usually you'd use it by just running `boot build` which will generate your static site.
This process is robust and great for production but it's slow and lacks feedback when you're developing your site.
In order to solve this problem we recommend following setup:

1. Have 2 separate tasks for building dev version and production version. E.x. `build-dev` and `build`.
2. Include [boot-http](https://github.com/pandeiro/boot-http) into your `build.boot` file. This will enable serving your site using web server.
3. Create task `dev` that will call `build-dev` on any change to your source files:

```clojure
  deftask dev
    []
    (comp (watch)
          (build-dev)
          (serve :resource-root "public")))
```
4. Run`boot dev`
In such setup you will have http web server serving your generated content that would be regenerated every time you change
your source files. So you'd be able to preview your changes almost immediately.


### Auto deployment

It's quite easy to setup automatic static site deployment.
E.x. you have GitHub repo for your blog and you are using `boot-s3` to sync files to Amazon S3.
In this case it's possible to setup flow in a way that every commit to GitHub would be build om Heroku using
perun and deployed to AWS S3.

Assuming you have setup similar to [example](https://github.com/hashobject/blog.hashobject.com/blob/master/build.boot#L31) in order to achieve this you need to:
 - create [Heroku](heroku.com) application for your GitHub repo with `build.boot` file
 - ensure that `build.boot` has `build` task that has tasks build and deploy tasks
 - specify `AWS_ACCESS_KEY` and `AWS_SECRET_KEY` envs. They are mandatory for the `boot-s3` plugin).
 - add boot/perun buildpack `heroku buildpacks:add https://github.com/hashobject/heroku-buildpack-perun`
 - enable GitHub integration https://devcenter.heroku.com/articles/github-integration
 - change your site in GitHub and see changes deployed to AWS S3 in few minutes


## TODO

  - [ ] improve readme
  - [ ] more plugins
  - [ ] create perun.io site using perun
  - [ ] create section with all sites build using perun

## Contributions

We love contributions. Please submit your pull requests.

## Contributor list

- [Martin Klepsch](https://github.com/martinklepsch)
- [Juho Teperi](https://github.com/Deraen)

## License

Copyright © 2013-2015 Hashobject Ltd (team@hashobject.com).

Distributed under the [Eclipse Public License](http://opensource.org/licenses/eclipse-1.0).
