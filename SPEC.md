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
    - **:name**
    - **:email**
- **:base-url**
    - Required by: *canonical-url*
    - Required by: *atom-feed*
    - Required by: *rss*
- **:language**
- **:description**
    - Used by: *atom-feed*
    - Used by: *rss*

## Post metadata

All posts have a filename which is used as a key to identify the post.

- **:name**
    - Required by: *atom-feed*
    - Used by: *rss* either this or description is required
- **:content** The post content
    - Set by: *markdown*
    - Used by: *atom-feed*
- **:description**
    - Used by: *rss* either this or name is required
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
- **:build-date** date when the site was generated
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
