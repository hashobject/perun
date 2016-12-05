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
    - Required by: *canonical-url*
    - Required by: *atom-feed*
    - Required by: *rss*
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
- **:has-content** Flag indicating that this file contains page content
    - Set by input parsing tasks (like `markdown`) on output files
    - Passed through by rendering tasks
- **:original-content** Contains the content from the original parsing task
    - Set by input parsing tasks (like `markdown`) on output files
    - Passed through by rendering tasks
- **:parsed** Contains the parsed file content
    - Set by input parsing tasks (like `markdown`) on input files
- **:description**
    - Used by: *rss* either this or title is required
- **:slug**
    - Set by: slug
    - Required by: *permalink* default fn
- **:permalink** relative url for the post
    - Set by: *permalink*
    - Used by: *render* as first option for output file name
    - Required by: *atom-feed*
    - Required by: *canonical-url*
- **:canonical-url** full url for the post
    - Set by: *canonical-url*
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
