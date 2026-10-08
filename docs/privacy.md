# Operator privacy policy

*Last updated: 6 October 2026. Applies to Operator 0.3.0-beta and later.*

Operator is a Matrix chat app for keypad phones. This page says what the app does with your information, in plain language. The short version: your messages go between your phone and your Matrix account provider (normally Beeper), encrypted; nothing goes to the people who make Operator.

## Who we are

Operator is an independent open-source project, published under the GNU General Public License v3.0. It is not affiliated with Beeper, Matrix.org or any phone manufacturer. There is no company behind it and no Operator server.

## What Operator sends, and where

| What | Where it goes | Why |
|---|---|---|
| Your username and password, when you sign in | Your Matrix server (Beeper's, or one you choose) | To sign in. The password is not stored on the phone. |
| Your recovery key, when you approve the phone | Used on the phone only, to unlock your encryption keys; never stored, never sent | To read your encrypted chats |
| Messages, photos, files, reactions, read receipts, typing | Your Matrix server, end-to-end encrypted | Normal chat |
| Contact lists and "start a chat" requests | Your Matrix server's bridge endpoints (Beeper only) | New chat |
| A registration for notifications: a random identifier for this install and the relay's address | Your Matrix server | So the server can tell the relay something arrived |
| A wake-up signal: the id of the chat and the id of the message, no content | The notification relay (ntfy.sh by default, or a server you choose), then your phone | So notifications arrive without Google services |
| A problem report, **only if you choose Report a problem** | Your email app, addressed to the project | Diagnosing a bug |

Nothing is sent to the Operator project automatically. There are no analytics, no crash reporting, no advertising identifiers, and no Google services.

## About the notification relay

Operator uses a small relay (a [ntfy](https://ntfy.sh) server) because keypad phones without Google services have no other way to be woken for a new message. The relay receives only "something arrived in chat X, message Y". It never receives message content, names or your account details. The default relay is the public ntfy.sh service, run by ntfy's maintainer under [its own privacy policy](https://ntfy.sh/privacy). You can point Operator at your own ntfy server in Settings → Notifications → Push → Push server. Donations to the project are intended to fund a dedicated relay.

## What a problem report contains

If you choose **Settings → Report a problem**, Operator drafts an email containing: the phone model, Android version, Operator version, screen size, whether you are signed in and to which server, the notification connection state, your theme settings, and the last few hundred lines of Operator's own technical log. Operator never writes message text, contact names, access tokens or recovery keys to that log. You see the email before sending and can delete anything from it.

## What is stored on the phone

- Your session (the token that keeps you signed in) and the key to the local database, in storage encrypted with a key held in the phone's hardware security module.
- Your encryption keys, chat list and message history, in a database encrypted at rest with that key.
- Thumbnails of photos you have viewed, and files you chose to open, in the app's private cache.
- Your settings.

Android's app backup is switched off, so none of this is copied to a cloud backup. **Signing out deletes all of it** and tells the server to forget this phone.

## What Beeper sees

Operator signs in to a Beeper account. Beeper's service connects to WhatsApp, Signal and the other networks on your behalf and places the messages in your Matrix account, encrypted so that Beeper cannot read them. What Beeper does with your account is governed by [Beeper's privacy policy](https://www.beeper.com/privacy), not this one.

## Children

Operator is not directed at children under 13, and the services it signs in to have their own age requirements.

## Changes

Changes to this policy are recorded in the project's changelog, and the date at the top is updated.

## Contact

Questions about privacy: use **Settings → Report a problem** in the app, or open an issue on the project's GitHub repository.
