# Selling Pro on Google Play (once, about 20 minutes)

Pro is a one-time purchase (`pro`, $9.99). The app buys it through Google Play; the server checks each
purchase with Google before switching the account to Pro, so a fake or refunded purchase never unlocks it.

Everything is built. It stays off until these steps are done: before that, **Get Pro** says the copy
wasn't installed from Play, and the server answers `billing_not_configured`.

## What you need first
- A Google Play developer account ($25 once) at https://play.google.com/console.
- The app created in Play Console with package `com.greasepencil.pairshot`, and a release build uploaded
  to **Internal testing** (Play only shows products for an app that has a build uploaded).
- A payments profile (Play Console → Settings → Payments profile), so you can sell things.

## 1. Make the product
Play Console → your app → **Monetize with Play → Products → One-time products → Create product**:
- Product ID: `pro` (exactly; it can never change)
- Name: `Pairshot Pro`. Description: `Unlimited links that never expire, CRM, webhooks and API. One payment.`
- Price: $9.99 (Play fills in local prices for every country). Save, then **Activate**.

## 2. Let the server check purchases (a service account)
1. Open https://console.cloud.google.com, pick the **Pairshot** project (the one used for Google sign-in).
2. **APIs & Services → Library**: search **Google Play Android Developer API** and click **Enable**.
3. **IAM & Admin → Service accounts → Create service account**: name `pairshot-play`, then Done (no roles needed).
4. Open it, go to **Keys → Add key → Create new key → JSON**. A `.json` file downloads. Keep it secret.
5. Play Console (the account-level home, not the app) → **Users and permissions → Invite new users**:
   paste the service account's email (`pairshot-play@….iam.gserviceaccount.com`). Under **App permissions**
   add Pairshot and tick **View financial data** and **Manage orders and subscriptions**. Invite.
   (It can take up to a day before Google lets it check purchases.)

## 3. Give the key to the server
In the prompt (paste the path to the downloaded file):
```
! cd ~/projects/b4after/server && PATH=~/.local/toolchain/node/bin:$PATH npx wrangler secret put PLAY_SERVICE_ACCOUNT < /mnt/c/Users/Uni/Downloads/<the-file>.json
```
Then delete the downloaded file. Secrets aren't in the code, and `wrangler deploy` doesn't remove them.

## 4. Test a purchase without paying
- Play Console → **Settings → License testing**: add your Gmail. Testers get a test card ("always approves",
  "always declines", "slow card") and are never charged.
- Install Pairshot from your **Internal testing** link (not from Android Studio: a debug build is a different
  app to Play). Settings → Account & CRM → **Get Pro**.
- Try the slow card too: the app says payment is pending, then Pro switches on by itself the next time the
  screen opens after it clears.

## How it behaves
- **Restore purchase** finds a Pro purchase the phone's Play account already owns (new phone, reinstall).
  The screen also does this quietly whenever it opens on a free account.
- One purchase unlocks one Pairshot account. Deleting cloud data frees it for the next sign-in.
- Refunds and chargebacks: the server checks Google's list once an hour and switches the account back to free.
- The server acknowledges every purchase (Google refunds unacknowledged ones after 3 days).
