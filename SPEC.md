# Perun SPEC

This document defines metadata used by built-in tasks.

- **Set by** fields are set by the mentioned tasks.
- **Required by** fields are mandatory. Global metadata can alternatively be
set for the tasks using task options.
- **Used by** fields are optional.

## Global metadata

- **:site-title**
    - Required by: *atom-feed*
    - Required by: *rss*
- **:author**
    - Required by: *atom-feed* if posts don't have author metadata
- **:author-email**
- **:base-url**
    - Must be in canonical form, that is end in `/`
    - Required by: *atom-feed*
    - Required by: *rss*
- **:doc-root**
    - Defaults to "public"
    - Permalinks will be determined relative to this directory
- **:language**
- **:description**
    - Used by: *atom-feed*
    - Used by: *rss*

## Post metadata

All posts have a path which is used as a key to identify the post.

- **:title**
    - Required by: *atom-feed*
    - Used by: *rss* either this or description is required
- **:content** The post content
    - Populated from file content
    - Only set in the `:entry` value of the map passed to `renderer` functions
- **:original-path** The path for the input file from which this entry is descended
    - Set by input parsing tasks (like `markdown`) on output files
    - Passed through by rendering tasks
- **:description**
    - Used by: *rss* either this or title is required
- **:slug**
    - Set implicitly based on a file's path
    - Required by: *permalink* default fn
- **:permalink** relative url for the post
    - Set implicitly based on a file's path
    - Required by: *atom-feed*
    - Required by: *canonical-url*
- **:uuid** The post's permanent ID.
     - Required by *atom-feed*
- **:canonical-url** full url for the post
    - Set implicitly based on a file's path, if a `:base-url` is present in global metadata
    - Used by: *atom-feed*
    - Used by: *rss*
- **:author**
    - Required by: *atom-feed*, unless global value is set
- **:author-email**
    - Used by: *atom-feed*
    - Used by: *rss*
- **:draft**
    - Used by: *draft*
- **:date-build** date when the site was generated
    - Set by: *build-date*
    - Used by: *sitemap*
- **:date-created**
    - Required by: *atom-feed*
- **:date-published**
    - Used by: *collection* to sort the posts
    - Used by: *atom-feed*
    - Used by: *rss*
- **:date-modified**
    - Used by: *atom-feed*
    - Used by: *sitemap*
- **:original**
    - Set by: *markdown*
- **:ttr**
    - Set by: *ttr*
- **:include-atom**
    - Set by: *markdown*, true by default
    - Used by: *atom-feed*
- **:include-rss**
    - Set by: *markdown*, true by default
    - Used by: *rss*
- **:tags**
    - Used by: *tags*, *atom-feed*
- **:tag**
    - Set by: *tags*, on the pages generated for each tag listed
- **:page**
    - The number of this page
    - Set by: *paginate*
    - Set by: *atom-feed*
- **:first-page**
    - The filename of the first page in this group of pages
    - Set by: *paginate*
    - Set by: *atom-feed*
- **:last-page**
    - The filename of the last page in this group of pages
    - Set by: *paginate*
    - Set by: *atom-feed*
- **:prev-page**
    - The filename of the previous page in this group of pages, if it exists
    - Set by: *paginate*
    - Set by: *atom-feed*
- **:next-page**
    - The filename of the next page in this group of pages, if it exists
    - Set by: *paginate*
    - Set by: *atom-feed*
- **:io.perun/trace**
    - Conjed onto by every task that modifies metadata
    - Serves as a record of tasks to touch a file
