# Maintainers Guide

This guide is intended for *maintainers* — anybody with commit access to the
repository.

## Workflow for releasing

### Update the Changelog

You would need to convert the `Unreleased` section to a tagged (released)
section and fill up the missing entries, if Pull Request authors have not done
it themselves already.

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

Each version gets a changelog entry in the project's `CHANGELOG.md` file. Not
all changes need to be noted; things like coding standards fixes, continuous
integration changes, or typo fixes do not need to be communicated. However,
anything that falls under an addition, deprecation, removal, or fix MUST be
noted. Please provide a succinct but *narrative* description for the change you
merge. Once written, commit the `CHANGELOG.md` file against your local branch.

### Deploy release artifact

In order to deploy a release jar to Clojars, we need to accomplish two things:

    * update the `version.properties` file
    * tag a commit (preferably both tag and commit should be signed)

This can be accomplished with the following commands:

```shell
boot version --patch inc --pre-release empty
perun_version=$(grep "VERSION" version.properties | cut -d'=' -f2)
git commit --all --message "Release $perun_version"
git tag --sign "$perun_version" --message "Release $perun_version"
```

Of course the messages can be arbitrary but we recommend following the above
guideline.

Once that's done you are ready to push everything:

```shell
git push --follow-tags
```

The CI/CD pipeline will take care of the rest, you can check the progress at:

https://app.circleci.com/pipelines/github/hashobject

## Workflow for snapshots

A snapshot artifact will be deployed every time a commit is pushed to master.

The CI/CD pipeline validates that the version in `version.properties` contains
the `-SNAPSHOT` suffix. The pipeline fails if missing.

After a release you would usually bump the version and assign it the suffix
this way:

```shell
boot version --patch inc --pre-release snapshot
```

