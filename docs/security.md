# Security and privacy notes

What Operator does with your data, what protects it, and what still needs work. Written for people deciding whether to trust the app, and for contributors.

## Trust model in one paragraph

Operator is a Matrix client. Your messages are end-to-end encrypted by Trixnity (the Matrix SDK) using the Matrix Olm/Megolm protocol; Operator contains no cryptography of its own. The homeserver (Beeper's, or your own) sees encrypted events plus ordinary Matrix metadata. The push server (the public ntfy.sh by default; a server of your own from Settings, and a project-run relay once donations allow) sees only that *something* arrived: the pusher is registered with `event_id_only`, so pushes carry a room id and an event id, never content or keys. There is no Operator server, no analytics, no crash reporting, and no Google services.

## What leaves the phone, and where to

| Data | Goes to | Why |
|---|---|---|
| Encrypted messages, read receipts, typing, device keys | Your homeserver | Normal Matrix operation |
| Your Beeper email, then the emailed code | `api.beeper.com` | Beeper's email-code sign-in (see "Beeper's private API") |
| Pusher registration (app id, a random per-install topic, the gateway URL) | Your homeserver | So the server can wake the phone |
| Push wake-ups (room id + event id, no content) | ntfy server → phone | The wake-up signal |
| Bridge contact lists and "open chat" requests | Your homeserver's bridge provisioning endpoints (Beeper only) | New chat, contact names |
| Downscaled photos you chose to view | Downloaded from your homeserver's media store, decrypted on the phone | On-demand media |

Nothing is sent to the Operator project or to third parties.

## On-device storage

- The database (Trixnity's Room store: encryption keys, room state, message history, and the session's access token) is encrypted at rest with SQLCipher. Its passphrase, and a copy of the access token used for raw HTTP calls, live in `EncryptedSharedPreferences` whose master key is held in the Android keystore (`SessionVault`). A database written by a build before 0.3.0 is converted in place at first start.
- Photo thumbnails you have viewed, and files you chose to open, sit in the app's private cache directory, decrypted. Files handed to another app go through a `FileProvider` and are replaced on the next open.
- Settings (theme, notification preferences, mute rules, the ntfy topic) are ordinary app-private preferences; none of them is secret on its own.
- Android backup is disabled (`allowBackup="false"`), so no copy of the session or keys goes to a cloud backup.
- Signing out deletes the database, caches and preferences, clears the vault (so the next sign-in gets a fresh database key), removes the pusher, and logs the device out server-side.

## Logging

Rule (SPEC §9): never log message contents, access tokens or recovery keys, in any build. Push handlers log counts, never ids that identify a chat. The ported Chats code has verbose debug logging behind a flag that is off by default; it is still to be audited line by line before the first public release.

## Beeper's interfaces

Sign-in is standard Matrix password login to `matrix.beeper.com`. Beeper's email-code login (`api.beeper.com/user/login*`, bearer `BEEPER-PRIVATE-API-PLEASE-DONT-USE`) was closed to third-party clients on 2 October 2026 and is no longer used; the code remains in `core-beeper` in case Beeper reopens it. Setting a password for an account that has none uses the standard Matrix `account/password` email flow.

Bridge provisioning (contacts, resolve_identifier, create_chat under `/_matrix/client/unstable/com.beeper.bridge/...`) is the same interface Beeper's own clients use but is not documented for third parties. It may change without notice; Operator degrades to "New chat shows existing chats only" if it does. Beeper has been told about Operator.

## Device approval

A new phone must be approved before it can read encrypted history. Operator offers the recovery key first (typed on the phone, never stored, never logged, sent only to Trixnity's key-store code) and emoji approval from another device second. Chats' permissive room-key request handlers are retained: the phone answers key requests from *any device of the same user*, verified or not, because Beeper accounts do not reliably cross-sign. This is a deliberate trade: convenience for same-account devices against the (small) risk of a compromised session on your own account requesting keys. Worth revisiting once Beeper's cross-signing behaviour is understood.

## Network

- TLS everywhere; no cleartext traffic allowed (platform default for this target SDK). Android 8 devices have TLS 1.2 only; the servers in use accept it.
- The ntfy topic is a 128-bit random value and works as a bearer token for the wake-up stream; it is stored in app-private preferences and rotated when the server changes.

## Supply chain

- Dependencies are pinned in `gradle/libs.versions.toml`. Trixnity, Ktor, Room, AndroidX, UnifiedPush connector, OkHttp. No Google Play Services, no Firebase.
- Release process and signing fingerprint: `docs/releasing.md`. **To do:** dependency verification (Gradle's `verification-metadata.xml`) and reproducible builds for F-Droid.

## Reporting a problem

See `SECURITY.md`.
