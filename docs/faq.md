# Operator: frequently asked questions

## What is Operator?

A chat app for keypad phones (D-pad, soft keys, T9) that shows all your chats from WhatsApp, Signal, Telegram, Messenger and more in one place, with notifications that don't need Google. It's a Matrix client that signs in to a Beeper account.

## Why do I need Beeper?

Because WhatsApp, Signal and the others are closed systems. Beeper connects to them on your behalf and brings the messages into an encrypted Matrix account. Operator signs in to that account. Without Beeper, Operator could only talk to other Matrix users. Beeper is free for this.

## Is it secure?

Yes, with the same honest caveats as any chat app.

- Messages are end-to-end encrypted by the Matrix protocol. Beeper's servers carry them but cannot read them.
- Operator's own database on the phone is encrypted, with the key kept in the phone's hardware security chip.
- The notification relay only ever receives "something new arrived", never the message.
- Operator has no servers, no analytics and no Google services, and the code is open for anyone to inspect.

The caveat: Beeper's bridges do talk to WhatsApp and Signal for you, so the translation happens on Beeper's servers. That's inherent in what Beeper does, and it's covered in Beeper's own privacy policy. See Operator's [privacy policy](privacy.md) for the full picture.

## Which phones does it work on?

Any Android phone running Android 8 or later with a D-pad should, in theory, work. I haven't teested many though. It is tested on the TTfone TT990 (Android 14) and on a smaller 240×320 keypad phone. Touchscreens work too, but nothing requires one. It does not run on KaiOS or on non-Android feature phones.

## Why does the first sync take so long?

The first time, Operator downloads your chat list and the keys to decrypt your history, which can be several minutes on a slow phone or a big account (or both). Leave the phone on and on Wi-Fi. After that it's quick.

## Some messages say "Can't open this message yet"

Their encryption keys haven't reached the phone yet. Open Beeper Desktop on your computer for a few minutes and the keys will be shared across. This mostly affects old messages from before you set the phone up.

## I'm not getting notifications

In Operator, go to Settings → Set up your phone and make sure every item says Done. Many MediaTek-based phones, including the TT990, have a feature called DuraSpeed that closes background apps; Operator must be switched on there to stop it being closed automatically. If notifications arrive but late, check Settings → Notifications → Push, which shows whether the relay is connected and can send a test.

## Why is there a public server (ntfy.sh) involved?

Phones without Google services have no built-in way to be woken for a new message. Operator uses a tiny relay, a ntfy server, for that signal. The default is the public ntfy.sh. The relay sees only the chat and message identifiers, never content. You can use your own ntfy server from Settings. Donations to the project go towards launching a dedicated relay.

## Does it mark messages as read when I preview them?

No. The sideways preview (on phones that rotate) reads from the phone's own store and never opens the chat, so no read receipt is sent. This gives you a way to see a message, but not notify the sender that you've seen it yet.

## Can I use it with a Matrix account that isn't Beeper?

Yes: "Use a different Matrix server" on the welcome screen signs in to any Matrix server with a username and password. You'll get your Matrix chats but, without Beeper, not WhatsApp and friends. New chat then takes a full Matrix ID (for example @name:matrix.org) and starts an encrypted direct chat. Tested with a matrix.org account: sign-in, recovery key, sync, notifications, sending, and starting a chat all work. Beeper's contact list and bridges are not available there, naturally.

## Can it make calls, record voice notes, or start groups?

Not yet. Voice notes you receive can be played. Calls are unlikely on this hardware. Creating groups is on the list.

## What does Beeper think about this?

Operator uses Beeper's standard Matrix sign-in and the same bridge interfaces Beeper's own apps use. Like any third-party app, Operator depends on Beeper continuing to allow this; if that changed, the "different Matrix server" path would still work.

## How much battery does it use?

Operator is designed to sit quietly: one lightweight connection to the relay, and nothing running unless a message arrives. A measured figure from the test phones will be published here.

## Is it free? Can I donate?

Free, and it will stay free: it is licensed under the GPL-3.0, which means anyone who distributes a modified version must publish their changes too, and must use their own name and icon rather than Operator's. Companies that want a rebranded or closed build can license it commercially, which helps fund the project. You can donate at [ko-fi.com/operatorchat](https://ko-fi.com/operatorchat) or through [GitHub Sponsors](https://github.com/sponsors/OperatorChat); both are at the top of Settings in the app. The money goes to the notification relay and domain, not to a company.

## How do I report a bug?

Settings → Report a problem drafts an email with the technical details (no messages). Or open an issue on GitHub with the phone model and what the screen said.
