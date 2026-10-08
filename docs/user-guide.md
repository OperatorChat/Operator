# Operator user guide

From nothing to a working keypad phone. Allow about 30 minutes.

## What you need

- A keypad phone running Android 8 or later, such as the TTfone TT990, charged and on Wi-Fi, plus its USB cable.
- A computer to set up Beeper on. You only need it for the setup.
- Access to the email account you'll use for Beeper.

There are four parts: set up Beeper, prepare your details, copy Operator and your details to the phone, and sign in.

## Part 1. Set up Beeper (on a computer)

Beeper is the free service that links your chat apps together. Operator can't connect to WhatsApp, Signal and the rest by itself, so this comes first.

1. Go to beeper.com and download Beeper Desktop for Windows, Mac or Linux. Install it and open it.
2. Choose **Create account**. Enter your email address, type in the code Beeper emails you, and pick a username when asked. Write the username down; Operator needs it.
3. Connect each chat app you use:
   - **WhatsApp**: Beeper shows a QR code. On your old phone, open WhatsApp → Settings → Linked devices → Link a device, and scan it.
   - **Signal**: the same idea, from Signal → Settings → Linked devices.
   - **Telegram, Instagram, Messenger** and others: follow Beeper's prompts.
4. Wait a few minutes until your chats appear in Beeper Desktop.

Leave Beeper Desktop installed. It needn't run all the time once Operator is set up, but it's useful to have.

## Part 2. Prepare your details (on the computer)

Typing long codes on a keypad is slow and error-prone, so you'll put the three things Operator needs into a small text file and copy it to the phone with the app. Then you paste instead of typing. You delete the file at the end.

**Your recovery key.** Your chats are encrypted; the recovery key lets a new device read them. It's 48 letters and numbers in groups of four.

1. In Beeper Desktop, open **Settings** (the gear icon), then **Beeper Profile**.
2. Press the menu button at the top right and choose **Show Recovery Key**. Copy it.

If it isn't there, Beeper lets you create a new one from the same menu.

**Your password.** Beeper normally signs you in by emailed code, so your account has no password yet. Operator needs one. Decide on it now: at least 8 characters, and something you could type on a keypad if you had to. You'll set it in Part 4.

**The file.** Make a plain text file called `operator.txt` with three lines: your Beeper email address, the password you've chosen, and the recovery key. Save it next to the Operator APK you'll download in Part 3.

## Part 3. Copy Operator and your details to the phone

Operator isn't in the Play Store. Get the APK from the project's GitHub releases page (or install through F-Droid or Obtainium, in which case skip the APK and copy only the text file).

**Over the USB cable**

1. Plug the phone into the computer and unlock it.
2. Pull down from the top of the phone's screen, tap the "USB charging" notification and choose **File transfer**.
3. On Windows the phone appears in File Explorer; open Internal shared storage → Download. On a Mac, install the free OpenMTP first (openmtp.ganeshrvel.com); the phone's folders appear in it.
4. Drag the APK and `operator.txt` into **Download**. Unplug the phone.

If the cable isn't an option, email both files to yourself and download them from the phone's browser. Bear in mind that puts your password and key through your email.

**Allowing the install**

5. On the phone, open **Files** (or My Files, File Manager), go to **Downloads**, and select the Operator file ending in `.apk`.
6. The phone says it isn't allowed to install unknown apps from this source. Choose **Settings**, switch on **Allow from this source**, press Back.
7. Select the file again, choose **Install**, then **Open**.

## Part 4. Sign in on the phone

**The keys.** Up and down move the highlight; the centre key selects. The left soft key (top left under the screen) does what the label at the bottom left says, usually **Options**; the right soft key does what the bottom right says, usually **Back**. The green call key jumps to the message box in a chat.

**Pasting instead of typing.** When Operator asks for your email, password or key: press Home, open Files → Downloads → `operator.txt`, press and hold the value with your finger, drag the handles to select just the value, and choose **Copy**. Press Home, reopen Operator (it carries on where you were), press and hold the empty box, choose **Paste**.

1. On the welcome screen, select **Sign in with Beeper**.
2. Your account has no password yet, so select **Set a password by email** below the boxes.
3. Enter your Beeper email address and the password you decided on in Part 2. Select **Email me the link**.
4. Beeper emails you a link to confirm it's you. Open it on your computer or old phone. A page says your email is confirmed; it asks for nothing.
5. On the phone, select **Done, I opened the link**. Operator saves your password and says "Password set. Now sign in with it."
6. Enter your Beeper username and your password, and select **Sign in**.
7. Operator asks for your recovery key. Paste it, or type the 48 characters exactly as shown. Spaces don't matter; capital and small letters do. Select **Approve this phone**. If it says it's waiting for the first sync, leave it for a few minutes.
8. Operator downloads your chats. The first time can take several minutes. Leave the phone on and on Wi-Fi.
9. Work down the **Set up your phone** checklist, agreeing to what the phone asks: allow notifications; keep running in the background; don't pause if unused; DuraSpeed (on MediaTek phones such as the TT990: in the phone's Settings, scroll to the bottom, open DuraSpeed and switch Operator on); and turn off notifications in any other messaging apps on the phone so each message buzzes once.
10. Select **Continue**. Your chat list appears.
11. **Delete the file**: Files → Downloads → press and hold `operator.txt` → Delete. Operator keeps what it needs encrypted inside the app.

Send yourself a message from another phone and watch it arrive.

## Everyday use

- **Open a chat**: highlight it, press the centre key.
- **Reply**: in a chat, press the green key, type, press the left soft key (**Send**).
- **Do something with a message**: highlight it, press the centre key. Reply, Copy text, reactions, and for your own messages Edit and Delete. Photos offer **View photo**; files **Open file** and **Save to Downloads**; links **Open link**; people mentioned in the message **Message <name>**.
- **Chat options**: the left soft key (**Options**) in a chat: photos, mentions, mute (including "Mute, except mentions"), pin, archive, details.
- **New chat**: the right soft key on the chat list, then type part of a name.
- **Settings**: Options on the chat list → Settings. Appearance has four looks, light and dark, three text sizes, and switches for animations and turn-to-preview.
- **Preview without opening** (phones that rotate): highlight a chat and turn the phone on its side. Turn it back to close. Nothing is marked read.

## If something goes wrong

- **"Beeper has no account with that email address"**: check the email you used to create the account.
- **The password link never arrives**: check spam, wait five minutes, select **Email me the link** again.
- **The recovery key is rejected**: it's case-sensitive. The key never contains a zero, capital O, capital I or lower-case L, so one of those is a typo.
- **"Can't open this message yet"**: the keys are still arriving. Open Beeper Desktop for a few minutes.
- **No notifications**: Settings → Set up your phone; every item should say Done.
- **Anything else**: Settings → Report a problem drafts an email with the technical details, never your messages.
