# Testing links and CRM before the real server exists

For now the server runs on your computer. Everything works the same as it will on Cloudflare,
except that the phone (and Zapier) has to be able to reach your computer.

## 1. Start the server (in WSL)

```
cd server
npm ci                 # first time only
npm run db:local       # first time, and after a new migration
npm run dev            # http://localhost:8787 (Ctrl+C stops it)
```

Data is kept in `server/.wrangler/state`. Delete that folder to start fresh.
Dev mode is on, so a phone can connect without Google sign-in, and the app has a
"Pretend this account is Pro" switch.

## 2. Let the phone reach it. Pick one:

**A. Same Wi-Fi (private, nothing exposed to the internet).** WSL needs to share the Windows network:

1. Make `C:\Users\Uni\.wslconfig` contain
   ```
   [wsl2]
   networkingMode=mirrored
   ```
   then run `wsl --shutdown` in PowerShell and open WSL again.
2. Allow the port once, in an Administrator PowerShell:
   `New-NetFirewallRule -DisplayName "Pairshot dev" -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow`
3. Find the PC's Wi-Fi address with `ipconfig` (e.g. `192.168.1.20`).
4. In the app: **Settings → Cloud & CRM → Server address** = `http://192.168.1.20:8787`, then Save and Connect.

Debug builds may use plain `http://`. Release builds only allow https.

**B. From anywhere, and needed for Zapier/Make to reach your inbound address:** a Cloudflare quick
tunnel. It needs no account, gives a temporary `https://….trycloudflare.com` address, and stops when you press Ctrl+C:

```
# download once: https://github.com/cloudflare/cloudflared/releases (cloudflared-linux-amd64)
cloudflared tunnel --url http://localhost:8787
```

Put the printed https address into the app. While it runs, anyone with that address can use your
test server, so only run it while you're testing. You get a new address each time.

## 3. What to try

1. Shoot a before and after, then tap **Send link to client** on the job. The share sheet opens with your message.
2. Open the link in a browser. Back in the app, the card says "Opened just now".
3. Hide a pair. The card says "You've changed things since sending". Tap **Update the link**. The link stays the same, and nothing is uploaded again.
4. Turn on airplane mode and send another job. It waits ("Waiting for signal"). Turn airplane mode off and it goes by itself, with a notification if the app is closed.
5. Send 4 jobs on the free plan. The 4th is refused with the monthly-limit message.
6. Turn on **Pretend this account is Pro**. Copy the inbound address and send it a job:
   ```
   curl -X POST <inbound address> -H 'content-type: application/json' \
     -d '{"id":"J-1","client_name":"Sam Lee","service":"Driveway","address":"4 Bay Rd"}'
   ```
   Tap **Sync now** (or reopen the app) and Sam Lee is in your jobs.
7. Add a webhook (for example from https://webhook.site) and tap **Test**, then send a link. You
   should see `gallery.published` arrive, and later `gallery.viewed`.
8. Make an API key and call `GET <server>/api/v1/galleries` with `X-Api-Key`.

## Automated checks

```
cd server && npm test        # 63 tests: limits, galleries, CRM parsing, webhooks, API, cleanup
cd android && ./gradlew :app:testDebugUnitTest

# the app's real network code against the running server:
PAIRSHOT_SERVER=http://localhost:8787 ./gradlew :app:testDebugUnitTest --tests '*EndToEndTest*'
```
