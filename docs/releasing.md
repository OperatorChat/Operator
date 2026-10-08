# Releasing Operator

How a version gets from the repository to people's phones, and where it is listed. Written so it is repeatable by anyone with the signing key.

## Before tagging

1. Update `versionCode` (add one) and `versionName` in `app/build.gradle.kts`.
2. Add the release's entry to `CHANGELOG.md`, and copy its user-facing lines to `fastlane/metadata/android/en-GB/changelogs/<versionCode>.txt` (F-Droid shows that file).
3. Build and install on at least one reference phone and go through: sign-in (debug build, so the real session is untouched), chat list, a chat, send, a notification arriving with the app in the background, Settings.
5. Commit, then tag: `git tag -a v0.3.0-beta -m "Operator 0.3.0-beta"` and push the tag.

## Building the release

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean :app:assembleOperatorRelease
```

Shipped builds are unshrunk (about 16 MB), which is what has been tested and what F-Droid builds. `-Pminify` turns R8 on for experiments; the shrunk build has not been verified against a real sign-in, so do not ship it without doing so.

Signing needs `keystore.properties` at the repository root (never committed), pointing at the keystore `~/.operator-signing/operator-release.jks`, alias `operator`. The certificate's SHA-256 fingerprint, published in the README, is `c978a97c7ce5eb6b4cd4cb14416e2f0fb2d15945c6f9795dd80cdc0bf7431f34`. Losing this key means future updates cannot install over existing ones, so it is backed up offline.

Rename the output to `operator-<version>.apk`.

## Publishing

1. **GitHub release** on the tag, with the APK attached and the changelog entry as the body. This is the source of truth; everything below follows from it. Update the "Download the APK" link and size at the top of `README.md` to the new file name.
2. **IzzyOnDroid** picks up new GitHub releases automatically once the app is in its index. First-time inclusion is requested at https://apt.izzysoft.de/fdroid/index/info (the APK must be under 30 MB, which it is).
3. **F-Droid** builds from the tag using the metadata file in the fdroiddata repository. New versions are picked up automatically when the metadata's auto-update mode is set to tags. The first submission is a merge request to https://gitlab.com/fdroid/fdroiddata adding `metadata/io.github.operatorchat.operator.yml` (a ready draft is in `fdroid/` here). It needs a gitlab.com account to open the merge request. Expect the review to take weeks.
4. **Obtainium** users get the GitHub release directly; nothing to do.

## Where Operator is listed

| Place | Updated by | Notes |
|---|---|---|
| GitHub releases | Us, manually | Source of truth. Signed with our key. |
| IzzyOnDroid | Automatic from GitHub releases | Our signature. Usually within a day or two. |
| F-Droid | Automatic from tags, built by F-Droid | F-Droid's signature. Listing text comes from `fastlane/metadata`. Shows the "NonFreeNet" anti-feature because Operator depends on Beeper. |
| Obtainium | Automatic from GitHub releases | Not a store; a user-side updater. |

Google Play: not listed. If that ever changes, the "Support Operator" row must be hidden in that build (Play forbids external donation links) and a data-safety form and privacy-policy URL are required.

## After publishing

- Install the published APK on a phone from the download, not from the build directory, and check it opens and signs in.
- Tell the beta testers.
- Open the next changelog entry as "unreleased".
