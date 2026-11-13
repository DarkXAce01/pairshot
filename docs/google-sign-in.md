# Setting up Google sign-in (once, about 10 minutes)

Pairshot asks for Google sign-in only at the first Send. The server uses it to make sure there is
one account per person, and one free account per phone. It's free, and Google doesn't need a card for it.

You make three things in Google Cloud: a project, a **Web** client (its ID goes into the app and the server),
and an **Android** client (this proves the app really is yours).

## 1. Project and consent screen

1. Open https://console.cloud.google.com and sign in with the Google account that will own Pairshot.
2. Use the project picker (top left) → **New project** → name it `Pairshot` → Create, then select it.
3. Go to **APIs & Services → OAuth consent screen** (also called **Google Auth Platform**) → **Get started**.
   - App name: `Pairshot`. Support email: yours.
   - Audience: **External**.
   - Contact email: yours. Agree, then Create.
4. Under **Branding**, optionally add the app logo and your privacy page:
   `https://pairshot.pairshot-server.workers.dev/privacy`.
5. Under **Audience**, while testing, either click **Publish app**, or add your own Gmail as a test user.
   Sign-in only asks for name and email, so Google doesn't need to review the app.

## 2. The Web client (the ID the app and server use)

**Clients → Create client → Application type: Web application** → name it `Pairshot server` → Create.
You don't need any URLs. Copy the **Client ID**. It looks like `1234567890-abc…apps.googleusercontent.com`.

## 3. The Android client (one per signing key)

You need the SHA-1 fingerprint of the key that signs the app.

**The debug build you run from Android Studio:** in Windows PowerShell:
```
keytool -list -v -keystore $env:USERPROFILE\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```
(`keytool` is in Android Studio's `jbr\bin` folder if it isn't on your PATH.) Or in Android Studio,
open the Gradle panel and run **app → Tasks → android → signingReport**. Copy the `SHA1:` line.

Then go to **Clients → Create client → Android**:
- Package name: `com.greasepencil.pairshot.debug` (the debug build has `.debug` on the end)
- SHA-1: the one you copied

Later, for the Play Store, make a second Android client with package `com.greasepencil.pairshot`
and the SHA-1 shown in Play Console → **Test and release → App integrity → App signing**.

## 4. Give me (or put in) the Web client ID

Paste the Web client ID into the chat and I'll do the rest. Or do it yourself:

- **App:** in `android/gradle.properties`, set `pairshot.googleWebClientId=<the Web client ID>`.
- **Server:** in `server/wrangler.jsonc`, set `"GOOGLE_CLIENT_ID": "<the Web client ID>"` and `"DEV_MODE": "false"`,
  then run `npx wrangler deploy`.

Once `DEV_MODE` is off, test sign-in and the "Pretend Pro" switch stop working on the live server.
Phones that were already connected keep working. The first time one of them signs in with Google,
it keeps its links. Local `npm run dev` stays in test mode.

## If sign-in fails

- **"Google sign-in didn't work"** right away: the Android client's package name or SHA-1 doesn't match
  the build you installed. A debug build from another computer has a different SHA-1, so add that one too.
- **"That Google sign-in didn't work. Try again."** (from the server): the server's `GOOGLE_CLIENT_ID`
  isn't the same Web client ID as the app's.
- **Nothing happens / "Add a Google account":** the phone has no Google account signed in.
