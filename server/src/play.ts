import type { Context } from "hono";
import { importPKCS8, SignJWT } from "jose";
import { publicAccount } from "./auth";
import type { Account, AppEnv, Env } from "./env";
import { fail, now } from "./util";

/**
 * Pro is a one-time Google Play purchase. The phone buys it, then sends the purchase token here; we ask
 * Google whether it's real and paid, acknowledge it (or Google refunds it after 3 days), and switch the
 * account to Pro. A token unlocks one account. Refunds are picked up by [checkVoided] and switch it back.
 */

const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

interface ServiceAccount { client_email: string; private_key: string; token_uri?: string }

let cached: { key: string; token: string; until: number } | undefined;

/** An OAuth access token for the Play Developer API, from the service account key. */
async function accessToken(env: Env): Promise<string> {
  const raw = env.PLAY_SERVICE_ACCOUNT!;
  if (cached && cached.key === raw && cached.until > now() + 60_000) return cached.token;
  let sa: ServiceAccount;
  try {
    sa = JSON.parse(raw);
  } catch {
    throw new Error("PLAY_SERVICE_ACCOUNT isn't valid JSON");
  }
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const at = Math.floor(now() / 1000);
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/androidpublisher" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(at)
    .setExpirationTime(at + 3600)
    .sign(await importPKCS8(sa.private_key, "RS256"));
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!res.ok) throw new Error(`Google token: ${res.status} ${await res.text()}`);
  const j = (await res.json()) as { access_token: string; expires_in: number };
  cached = { key: raw, token: j.access_token, until: now() + j.expires_in * 1000 };
  return j.access_token;
}

async function google(env: Env, path: string, init: RequestInit = {}): Promise<Response> {
  const token = await accessToken(env);
  return fetch(`${API}/${encodeURIComponent(env.PLAY_PACKAGE)}/${path}`, {
    ...init,
    headers: { ...(init.headers ?? {}), authorization: `Bearer ${token}` },
  });
}

/** https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products */
interface ProductPurchase {
  purchaseState?: number; // 0 purchased, 1 cancelled, 2 pending
  acknowledgementState?: number; // 0 not yet, 1 done
  orderId?: string;
  purchaseTimeMillis?: string;
  productId?: string;
}

const productPath = (env: Env, token: string) =>
  `purchases/products/${encodeURIComponent(env.PRO_PRODUCT_ID)}/tokens/${encodeURIComponent(token)}`;

/** POST /v1/pro/claim { purchaseToken }: turns a paid Play purchase into Pro on this account. */
export async function claim(c: Context<AppEnv>) {
  const account = c.get("account");
  const env = c.env;
  const body = await c.req.json<{ purchaseToken?: unknown; productId?: unknown }>().catch(() => ({}) as Record<string, unknown>);
  const token = body.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 4096) fail(400, "bad_token", "That purchase didn't come through properly.");
  if (body.productId !== undefined && body.productId !== env.PRO_PRODUCT_ID) fail(400, "bad_product", "That isn't the Pro purchase.");
  if (!env.PLAY_SERVICE_ACCOUNT) fail(501, "billing_not_configured", "Buying Pro isn't open yet.");

  const owner = await env.DB.prepare(`SELECT account_id, state FROM purchases WHERE token = ?1`).bind(token).first<{ account_id: string; state: string }>();
  if (owner && owner.account_id !== account.id) {
    fail(409, "used_elsewhere", "This Pro purchase is already on another Pairshot account. Sign in with the Google account you used then.");
  }
  if (owner?.state === "voided") fail(402, "refunded", "This purchase was refunded, so Pro is off.");

  const res = await google(env, productPath(env, token));
  if (res.status === 400 || res.status === 404 || res.status === 410) fail(402, "not_found", "Google doesn't know this purchase.");
  if (!res.ok) {
    console.error("play verify", res.status, await res.text());
    fail(502, "google_unavailable", "Couldn't reach Google Play to check the purchase. It'll try again.");
  }
  const p = (await res.json()) as ProductPurchase;
  if (p.purchaseState === 2) return c.json({ status: "pending", account: publicAccount(c, account) }, 202);
  if (p.purchaseState !== 0) fail(402, "not_paid", "This purchase was cancelled.");

  if (p.acknowledgementState !== 1) {
    const ack = await google(env, `${productPath(env, token)}:acknowledge`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
    // Unacknowledged purchases are refunded after 3 days, so a failure here must be retried.
    if (!ack.ok && ack.status !== 400) {
      console.error("play ack", ack.status, await ack.text());
      fail(502, "google_unavailable", "Couldn't confirm the purchase with Google Play. It'll try again.");
    }
  }

  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO purchases (token, account_id, product_id, order_id, state, purchased_at, created_at) VALUES (?1, ?2, ?3, ?4, 'active', ?5, ?6)
       ON CONFLICT (token) DO NOTHING`,
    ).bind(token, account.id, env.PRO_PRODUCT_ID, p.orderId ?? null, p.purchaseTimeMillis ? Number(p.purchaseTimeMillis) : null, now()),
    env.DB.prepare(`UPDATE accounts SET plan = 'pro' WHERE id = ?1`).bind(account.id),
  ]);
  const updated: Account = { ...account, plan: "pro" };
  return c.json({ status: "pro", account: publicAccount(c, updated) });
}

/**
 * Cron, about once an hour: refunds and chargebacks from the last 30 days switch accounts back to free
 * (unless they hold another good purchase).
 */
export async function checkVoided(env: Env, at = now()): Promise<number> {
  if (!env.PLAY_SERVICE_ACCOUNT) return 0;
  let page: string | undefined;
  let changed = 0;
  for (let i = 0; i < 10; i++) {
    const q = new URLSearchParams({ startTime: String(at - 30 * 86_400_000), maxResults: "1000" });
    if (page) q.set("token", page);
    const res = await google(env, `purchases/voidedpurchases?${q}`);
    if (!res.ok) {
      console.error("play voided", res.status, await res.text());
      return changed;
    }
    const j = (await res.json()) as { voidedPurchases?: { purchaseToken: string }[]; tokenPagination?: { nextPageToken?: string } };
    for (const v of j.voidedPurchases ?? []) {
      const row = await env.DB.prepare(`UPDATE purchases SET state = 'voided' WHERE token = ?1 AND state = 'active' RETURNING account_id`)
        .bind(v.purchaseToken).first<{ account_id: string }>();
      if (!row) continue;
      changed++;
      await env.DB.prepare(
        `UPDATE accounts SET plan = 'free' WHERE id = ?1
           AND NOT EXISTS (SELECT 1 FROM purchases WHERE account_id = ?1 AND state = 'active')`,
      ).bind(row.account_id).run();
    }
    page = j.tokenPagination?.nextPageToken;
    if (!page) break;
  }
  return changed;
}

/** Tests: forget the cached Google access token. */
export const resetPlayCache = () => { cached = undefined; };
