# Maintainers Guide

This guide is intended for *maintainers* — anybody with commit access to the repository.

Maintainers can choose to release new maintenance releases with each new patch, or accumulate
patches until a significant number of changes are in place.

## Workflow for releasing

### Update the Changelog

You would need to convert the `Unreleased` section to a tagged (released)
section and fill up the missing entries, if Pull Request authors have not done it themselves already.

The CHANGELOG.md files should looks similar to this:

```markdown
## Unreleased

...

## X.Y.Z (YYYY-MM-DD)

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

### Deploy the artifact

In order to deploy either a release or a snapshot from the `master` branch (the
only allowed) , we need to accomplish two things:

    * update the `version.properties` file, for instance:
    * tag a commit (preferably both tag and commit should be signed):

On linux/bash, this can be accomplished with the following commands:

```shell
boot version --patch inc --pre-release snapshot
perun_version=$(grep "VERSION" version.properties | cut -d'=' -f2)
git commit --all --message "Release $perun_version"
git tag --sign "$perun_version" --message "Release $perun_version"
```

At this point you are ready to push everything:

```shell
git push --follow-tags
```

The CI/CD pipeline will take care of the rest, you can check the progress at:

https://app.circleci.com/pipelines/github/hashobject

