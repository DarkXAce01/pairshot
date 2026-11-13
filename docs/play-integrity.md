# Play Integrity (stops fake apps making free accounts)

At sign-in the app asks Google for a Play Integrity token: "this is Pairshot from Google Play, on a real
phone". The server checks it. Scripts, modified copies and emulators can't get a good one, so they can't
make free accounts in bulk.

It's built and **off**. It needs the Play Console setup from [play-billing.md](play-billing.md) first
(the same service account does both jobs).

## Turn it on
1. Google Cloud console → Pairshot project → **APIs & Services → Library** → **Google Play Integrity API** → Enable.
2. Play Console → your app → **Test and release → App integrity → Play Integrity API** → **Link Cloud project**
   → pick the Pairshot project (number `118026851240`, already in `android/gradle.properties`).
3. In `server/wrangler.jsonc`, set `"INTEGRITY_MODE": "log"` and deploy. Sign-ins are checked and the result
   is logged, but nobody is refused. Watch it with:
   ```
   ! cd ~/projects/b4after/server && PATH=~/.local/toolchain/node/bin:$PATH npx wrangler tail
   ```
   A line like `integrity log app_not_recognized` means that sign-in would have been refused.
4. Once testers installing from your Play testing link pass cleanly, set `"INTEGRITY_MODE": "enforce"` and deploy.

## Good to know
- **Debug builds from Android Studio never pass** (Play doesn't know them). Keep the mode at `off` or `log`
  while you test that way.
- Only **Google sign-in** is checked, which happens once per phone, so the free quota (10,000 checks a day) is plenty.
- If Google's check is down, sign-in is let through (logged) rather than locking everyone out.
- Refused people see: "This copy of Pairshot can't send links. Install Pairshot from Google Play and try again."
