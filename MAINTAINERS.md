# Maintainers Guide

This guide is intended for *maintainers* — anybody with commit access to the repository.

Maintainers can choose to release new maintenance releases with each new patch, or accumulate
patches until a significant number of changes are in place.

## Workflow for releasing 

### Create a Changelog

Starting with v2.5 of all components, and v1 of new components such as Diactoros and Stratigility,
we follow [Keep a CHANGELOG](http://keepachangelog.com/). The format is simple:

```markdown
# CHANGELOG

## X.Y.Z - YYYY-MM-DD

### Added

- [#42](https://github.com/organization/project/pull/42) adds documentation!

### Changed

- Nothing.

### Deprecated

- Nothing.

### Removed

- Nothing.

### Fixed

- [#51](https://github.com/organization/project/pull/51) fixes
  Something to be Better.
```

Each version gets a changelog entry in the project's `CHANGELOG.md` file. Not all changes need to be
noted; things like coding standards fixes, continuous integration changes, or typo fixes do not need
to be communicated. However, anything that falls under an addition, deprecation, removal, or fix
MUST be noted. Please provide a succinct but *narrative* description for the change you merge. Once
written, commit the `CHANGELOG.md` file against your local branch.

### Tag the milestone

If a feature is merged to `master`, flag it for the next maintenance milestone (e.g.,
"2.0.4");

---TODO---

## Tagging

We recommend tagging frequently. Only allow patches to accumulate if they are not providing
substantive changes (e.g., documentation updates, typo fixes, formatting changes). You *may* allow
features to accumulate in the `develop` branch, particularly if you are planning a major release,
but we encourage tagging frequently.

### Maintenance releases

Tag new maintenance releases against the `master` branch.

First, create a branch for the new version:

```console
$ git checkout -b release/2.5.3
```

Next, update the release date for the new version in the `CHANGELOG.md`, and commit the changes.

Merge the release branch into `master` and tag the release. When you do, slurp in the changelog
entry for the new version into the release message. Be aware that header lines (those starting
with `### `) will need to be reformatted to the alternate markdown header format (a line of `-`
characters following the header, at the same width as the header); this ensures that `git` does
not interpret them as comments.

```console
$ git checkout master
$ git merge --no-ff release/2.5.3
$ git tag -s release-2.5.3
```

> #### Tag names
>
> The various component repositories that were created from the original monolithic ZF2 repository
> use the tag name format `release-X.Y.Z`. This is the format that has been in use by the Zend
> Framework project since inception, and we keep it in these repositories for consistency.
>
> New repositories, such as zend-diactoros and zend-stratigility, use simply the semantic version as
> the tag name, without any prefix.
>
> Before you tag, check to see what format the repository uses!

> #### Signed tags are REQUIRED
>
> Always use the `-s` flag when tagging, and make sure you have setup PGP or GPG
> to allow you to create signed tags. Unsigned tags _**will be revoked**_ if
> discovered.

The changelog entry for the above might look like the following:

```markdown
Added
-----

- [#42](https://github.com/organization/project/pull/42) adds documentation!

Changed
-------

- Nothing.

Deprecated
----------

- Nothing.

Removed
-------

- Nothing.

Fixed
-----

- [#51](https://github.com/organization/project/pull/51) fixes
  Something to be Better.
```

You will then merge to `develop`, as you would for a bugfix:

```console
$ git checkout develop
$ git merge --no-ff release/2.5.3
```

Next, you need to create a CHANGELOG stub for the next maintenance version. Use the
`zf-maintainer changelog-bump` command:

```console
$ path/to/maintainers/bin/zf-maintainer changelog-bump 2.5.4
```

Spot-check the `CHANGELOG.md` file, and then merge to each of the `master` and `develop` branches:

```console
$ git checkout master
$ git merge --no-ff -m "Bumped version" version/bump
$ git checkout develop
$ git merge --no-ff -m "Bumped master version" version/bump
```

> #### Conflicts
>
> Be aware that this last merge to the `develop` branch will generally result in a conflict, as, if
> you are doing things correctly, you'll have an entry for the next minor or major release in the
> `develop` branch, and you're now merging in a new empty changelog entry for a maintenance release.

Push the two branches and the new tag:

```console
$ git push origin master:master && git push origin develop:develop && git push origin release-2.5.3:release-2.5.3
```

Finally, remove your temporary branches:

```console
$ git branch -d release/2.5.3 version/bump
```

### Feature releases

When you're ready to tag a new minor or major version, you'll follow a similar workflow to tagging a
maintenance release, with a couple of changes.

First, you need to merge the `develop` branch to master:

```console
$ git checkout master
$ git merge develop
```

Assuming you've been following the workflow outlined in this document, this *should* work without
conflicts. If you see conflicts, it's time to read the workflow again!

At this point, you will proceed as you would for a maintenance release, with a
couple changes. In your `versions/X.Y.Z` branch, do the following:

- Check that there is not an empty stub or unreleased maintenance version in the
  `CHANGELOG.md`. For empty stubs, just remove the full entry; for an unreleased
  maintenance version, merge the entries with those for the new minor or major
  release.
- Update the `branch-alias` section of the `composer.json` to bump the
  `dev-master` and `dev-develop` releases. As an example, if you are preparing a
  new `2.8.0` release, `dev-master` would now read `2.8-dev` and `dev-develop` would
  read `2.9-dev`. For a new major version 3.0.0, these would become `3.0-dev`
  and `3.1-dev`, respectively.

After those and any other changes suggested in the "Maintentance releases"
section are made, you can prepare to merge to master and develop. However,
before pushing the branches and tags, do the following:

- Checkout the `develop` branch, and bump the CHANGELOG; use the `--base` argument of the
  `changelog-bump` command to specify the `develop` branch:

  ```console
  $ path/to/maintainers/bin/zf-maintainer changelog-bump 2.6.0 --base=develop
  ```

- Merge the `version/bump` branch to `develop`.

At that point, you can push the branches, tag, and remove all temporary branches.

## FAQ

### What if I want to merge a patch made against develop to master?

Occasionally a contributor will issue a patch against the `develop` branch that would be better
suited for the `master` branch; typically these are bugfixes that do not introduce any new features.
When this happens, you need to alter the workflow slightly.

- Checkout a branch against develop; use the pull request number, with the suffix `-dev`.

```console
$ git checkout -b hotfix/1234-dev develop
```

- Merge the patch against that branch.

```console
$ git merge <uri of patch>
```

- Checkout another branch against master. Use the pull request number, with no suffix.

```console
$ git checkout -b hotfix/1234 master
```

- Cherry-pick any commits for the patch in the new branch. You can find the sha1 identifiers for
  each patch in the pull request's "Commits" tab.

```console
$ git cherry-pick <sha1>
```

- Merge the new branch to master and develop just as you would for any bugfix.

```console
$ git checkout master
$ git merge hotfix/1234
$ git checkout develop
$ git merge hotfix/1234
```

- Since you did not merge the first branch, `hotfix/1234-dev` in our example, you'll need to use the
  `-D` switch when removing it from your checkout.

```console
$ git branch -d hotfix/1234
$ git branch -D hotfix/1234-dev
```

### What if I want to merge a patch made against master to develop?

Go for it. One reason for choosing `git-flow` is to simplify merges. Because all changes made
against `master` are backported to `develop`, you can safely merge any change issued against the
`master` branch directly to the `develop` branch without issues.

### What order should CHANGELOG entries be in?

CHANGELOG entries should be in reverse chronological order based on release date, and taking into
account *future* release date.

This means that on the `develop` branch, the top entry should always be the one for the version the
`develop` branch is targeting. Additionally, the `develop` branch should contain a stub for the next
version represented by the `master` branch:

```markdown
## 2.6.0 - TBD

### Added

- Nothing.

### Changed

- Nothing.

### Deprecated

- Nothing.

### Removed

- Nothing.

### Fixed

- Nothing.

## 2.5.3 - TBD

### Added

- Nothing.

### Changed

- Nothing.

### Deprecated

- Nothing.

### Removed

- Nothing.

### Fixed

- Nothing.
```

Following this practice ensures that as bugfixes are ported to the `develop` branch, merges *should*
occur without issue, or be untangled relatively easily.

If we decide to skip the maintenance release and go directly to a minor or major release, remove the
stub for the maintenance release when merging the `develop` branch back to `master`. This is best
accomplished by adding the `--no-commit` flag when merging, manually removing the stub from the
changelog and staging it, and then finalizing the commit:

```console
$ git merge --no-commit develop
# edit CHANGELOG.md
$ git add CHANGELOG.md
$ git commit
```

In the case that the `master` branch had bugfixes that were never released before a minor/major
release was cut, you'll need to merge the changelog entries for that release into the `develop`
branch's changelog. As an example, consider the following:

```markdown
## 2.6.0 - TBD

### Added

- Useful features that everyone will want.

### Changed

- Nothing.

### Deprecated

- Useless features that are no longer needed.

### Removed

- Nothing.

### Fixed

- Stuff that couldn't be fixed as they require additions.

## 2.5.3 - TBD

### Added

- Nothing.

### Changed

- Nothing.

### Deprecated

- Nothing.

### Removed

- Nothing.

### Fixed

- A bunch of stuff that was broken
```

The above would be merged to a single changelog entry for 2.6.0 which would look like this:

```markdown
## 2.6.0 - TBD

### Added

- Useful features that everyone will want.

### Changed

- Nothing.

### Deprecated

- Useless features that are no longer needed.

### Removed

- Nothing.

### Fixed

- Stuff that couldn't be fixed as they require additions.
- A bunch of stuff that was broken
```
