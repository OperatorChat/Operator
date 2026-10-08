# Security policy

Operator handles private conversations, so security reports are taken seriously and handled quickly.

## Reporting a vulnerability

Please do **not** open a public issue for a security problem. Use GitHub's private vulnerability reporting (the "Report a vulnerability" button under the repository's Security tab), or email unsaved-fax-even@duck.com, the address Settings → Report a problem uses. Include what you found, how to reproduce it, and the app version (Settings → About).

You will get an acknowledgement within a few days. Fixes ship as a new release with a note in the changelog; credit is given unless you prefer otherwise.

## Scope

- The Operator app and its modules (`app`, `core-matrix`, `core-beeper`, `core-push`).
- Problems in Trixnity, the Matrix SDK, should go to Trixnity (gitlab.com/connect2x/trixnity); we will update as soon as a fix is released.
- Problems with Beeper's service or bridges should go to Beeper.

## What the app does and does not do

See `docs/security.md` for the data flows, storage, logging rules and known trade-offs.
