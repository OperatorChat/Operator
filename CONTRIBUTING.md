# Contributing to Operator

Thank you for helping. Operator is small and opinionated, so please read this first.

## What Operator is for

Keypad phones, AKA dumbphones. Every screen must work with up, down, left, right, centre, two soft keys, Back, and the green call key, on a small screen, with no touch. If it also works with a finger, good, but the keypad comes first. The screen must always say what the two soft keys do.

## Ground rules

- **Android Views, not Compose.** The target phones are slow; Views are what runs well on them.
- **No Google.** No Play Services, Firebase, microG, or anything that needs them. Notifications go through ntfy or UnifiedPush.
- **Never implement cryptography.** Trixnity does Matrix encryption; Operator calls it.
- **Never log message content, names, access tokens or recovery keys**, in any build. Log counts and states instead. Problem reports are built from these logs and people read them.
- **Beeper-specific code lives in `core-beeper`.** `core-matrix` must work against any Matrix server.
- **Colours and sizes come from the theme** (`?attr/op*`, see `app/src/main/res/values/attrs.xml`). Never hard-code a colour in a layout or drawable. Outlines exist only in the High contrast theme.
- **English** in strings and documentation.
- **Small commits** with a message that says why, not just what.

## Building

Install Android Studio for its JDK 21 and the Android SDK (platform 36). Then:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS; adjust elsewhere
./gradlew :app:assembleOperatorDebug
```

The debug build installs alongside a release build (its id ends in `.debug`), so you can test without disturbing a phone's real sign-in. Install with `adb install -r`.

The toolchain (Gradle 9, AGP 8.12, Kotlin 2.3, Trixnity 5.8) is pinned to the combination proven in fenleon/chats. Please don't bump versions in a feature change.

## Testing on a phone

There are no emulator images for these phones, so test on hardware. The reference device is the TTfone TT990 (Android 14, 320×480); the layout is designed for 240×320 screens too. Useful facts: the centre key is ENTER, the left soft key is MENU, the right soft key is BACK, the green key is CALL. Some phones hide debug-level logs by default (`adb shell setprop log.tag D` turns them on until reboot).

If you drive the phone over adb, take a screenshot before pressing keys; focus is not always where you expect.

## Layout of the code

| Where | What |
|---|---|
| `app/src/main/kotlin/chat/operator/app/ui` | Screens. `MainActivity` hosts a stack of `Screen`s; `MenuScreen` is a titled list; `OptionsScreen` is the sheet. |
| `app/src/main/res` | Layouts, drawables, themes, strings. Themes in `values/themes.xml`, palettes in `values/colors.xml` and `values-night/colors.xml`. |
| `app/src/operator/res/values/brand.xml` | Everything a rebrand changes. |
| `core-matrix` | `MatrixRepository` (ported from Chats), models, notifier and push facades, `SessionVault` for secrets, `MentionLogic`. |
| `core-beeper` | `BeeperAuth` (password setup), `BeeperChats` (contacts, new chat). |
| `core-push` | `PushRouter`, `NtfyPushChannel`, `UnifiedPushChannel`, `MatrixPusher`. |
| `docs` | Notes, design mockup, user guide, FAQ, privacy, release process. |

## Sending a change

1. Open an issue first for anything beyond a small fix, so the approach can be agreed.
2. Branch from `main`, keep the change focused, build it, and try it on a phone.
3. Describe in the pull request what changed, why, and which phone you tested on, with a screenshot where the screen changed.

## Reporting bugs

Use the bug report template. Include the phone model, Android version, Operator version (Settings → About) and what the screen said. If you can, attach the report from Settings → Report a problem.

## Licence and contributor agreement

Operator is GPL-3.0-or-later, and the project also offers commercial licences so that organisations can fund it. To keep that possible, contributions are accepted on these terms, which you agree to by opening a pull request:

- You keep the copyright on your contribution.
- You license it to the project under the GPL-3.0-or-later, and you additionally grant Spanorak a perpetual, worldwide, royalty-free right to relicense your contribution as part of Operator, including under commercial terms.
- You confirm the contribution is your own work (or that you have the right to submit it) and that it carries no licence incompatible with the GPL.

If you can't agree to this for a particular change, say so in the pull request and we'll talk before anything is merged.

## Code of conduct

Be kind and assume good faith. This is a small project made in spare time for people who want a quieter phone.
