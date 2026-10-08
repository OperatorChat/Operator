# Changelog

Notable changes to Operator. Versions follow major.minor.patch.

## 0.3.1-beta (8 October 2026)

- Fixed: after approving the phone with a recovery key, the key-backup restore could give up once and never retry, leaving chats encrypted until the app was restarted. It now retries on a schedule.
- Build hygiene for F-Droid: pinned Gradle wrapper checksum, en-US listing metadata.

## 0.3.0-beta (8 October 2026)

First public beta.

- The local database and the signed-in session are now encrypted at rest (SQLCipher, key in the Android keystore).
- Settings → Report a problem drafts an email with device and version details and recent technical log lines, never message content.
- Settings → Donate rows for Ko-fi and GitHub Sponsors.
- The push relay server can be changed in Settings → Notifications → Push for people running their own ntfy.
- "Open file" on an app package now saves it to Downloads instead of launching the installer; Operator no longer requests the install-packages permission.
- Privacy policy, FAQ, user guide, contributing guide and release process added.
- Application ID changed to `io.github.operatorchat.operator` before the first public release.
- Licence: GPL-3.0-or-later.
- New chat accepts a full Matrix ID on any server and starts an encrypted direct chat; the non-Beeper path is tested against matrix.org.
- Recovery screen soft key reads "Approve" so it fits the chip.

## 0.2.0 (3 October 2026)

A redesign and a long list of features, all tested on keypad phones.

- New look: Inter typeface, one-surface window bars, no outlines, initials avatars, flat bubbles with status colours, grouped settings with glyphs, options as a sheet over the dimmed screen, soft-key chips, light motion with an off switch. Four themes, light and dark, three text sizes.
- Clearer message focus: a band across the row plus a soft ring; pinned chats grouped at the top of the list.
- Emoji reactions in a five-wide grid of fifteen; selecting one you've sent removes it.
- Links listed in a message's options and opened only when chosen.
- Files: Open file and Save to Downloads for any attachment; photo rows gain Save.
- Mentions: highlighted names, an "@ you" tag, "Mute, except mentions", a Mentions notification channel, an "@" badge in the chat list, a member picker when typing "@" or from "Mention someone". "Message <Name>" rows for people mentioned in a message.
- Turn to preview: on phones that rotate, landscape on the chat list previews the highlighted chat without marking it read; the D-pad follows the rotation.
- Options menus block the D-pad from reaching the screen beneath.

## 0.1.0 (2 October 2026)

First working build.

- Sign in with a Beeper username and password; set a password by email for accounts that have none; approve the phone with the recovery key.
- Chat list and chat screen driven by the keypad; send, reply, edit, delete for everyone, react, copy.
- Photos on demand with thumbnails; send photos from the gallery or camera; voice-note playback.
- Notifications through the built-in ntfy channel with a Matrix pusher, screen wake option, UnifiedPush as an opt-in alternative.
- Onboarding: syncing screen, phone setup checklist (notifications, battery, pause-if-unused, DuraSpeed, duplicate notifications).
