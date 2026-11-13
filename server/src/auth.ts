import type { Context, MiddlewareHandler } from "hono";
import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";
import { bump } from "./counters";
import { type Account, type AppEnv, type Env, isDev } from "./env";
import { fail, isUuidish, now, randomToken, sha256Hex } from "./util";

let googleKeys: JWTVerifyGetKey | undefined;

function keysFor(env: Env): JWTVerifyGetKey {
  if (env.GOOGLE_JWKS_JSON) return createLocalJWKSet(JSON.parse(env.GOOGLE_JWKS_JSON));
  googleKeys ??= createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
  return googleKeys;
}

/** Checks a Google sign-in token from the phone and returns who it is. */
export async function verifyGoogle(env: Env, idToken: string): Promise<{ sub: string; email: string | null }> {
  if (!env.GOOGLE_CLIENT_ID) fail(501, "google_not_configured", "Google sign-in isn't set up on this server yet.");
  try {
    const { payload } = await jwtVerify(idToken, keysFor(env), {
      issuer: ["accounts.google.com", "https://accounts.google.com"],
      audience: env.GOOGLE_CLIENT_ID.split(",").map((x) => x.trim()).filter(Boolean),
    });
    if (!payload.sub) throw new Error("no subject");
    return { sub: payload.sub, email: typeof payload.email === "string" ? payload.email : null };
  } catch {
    fail(401, "bad_google_token", "That Google sign-in didn't work. Try again.");
  }
}

async function createAccount(env: Env, fields: { googleSub: string | null; deviceId: string; email: string | null }): Promise<Account> {
  const id = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO accounts (id, google_sub, device_id, email, plan, inbound_token, created_at) VALUES (?1, ?2, ?3, ?4, 'free', ?5, ?6)`,
  ).bind(id, fields.googleSub, fields.deviceId, fields.email, randomToken(24), now()).run();
  return (await env.DB.prepare(`SELECT * FROM accounts WHERE id = ?1`).bind(id).first<Account>())!;
}

/**
 * POST /v1/auth  { deviceId, googleIdToken? }
 * One account per Google account, and one free account per phone. In dev mode the Google token is
 * optional and the phone alone is the account.
 */
export async function signIn(c: Context<AppEnv>) {
  const body = await c.req.json<{ deviceId?: unknown; googleIdToken?: unknown }>().catch(() => ({}) as Record<string, unknown>);
  const deviceId = body.deviceId;
  if (!isUuidish(deviceId)) fail(400, "bad_device", "Missing or odd device id.");
  const env = c.env;
  let account: Account | null;

  if (typeof body.googleIdToken === "string" && body.googleIdToken) {
    const google = await verifyGoogle(env, body.googleIdToken);
    account = await env.DB.prepare(`SELECT * FROM accounts WHERE google_sub = ?1`).bind(google.sub).first<Account>();
    if (!account) {
      const other = await env.DB.prepare(`SELECT email FROM accounts WHERE device_id = ?1 AND google_sub IS NOT NULL`)
        .bind(deviceId).first<{ email: string | null }>();
      if (other) {
        fail(409, "phone_has_account", `This phone already has a Pairshot account${other.email ? ` (${other.email})` : ""}. Sign in with that one.`);
      }
      // This phone used the app before Google sign-in existed (test mode): keep that account and its links.
      const deviceOnly = await env.DB.prepare(`SELECT * FROM accounts WHERE device_id = ?1 AND google_sub IS NULL`)
        .bind(deviceId).first<Account>();
      if (deviceOnly) {
        account = (await env.DB.prepare(`UPDATE accounts SET google_sub = ?2, email = ?3 WHERE id = ?1 RETURNING *`)
          .bind(deviceOnly.id, google.sub, google.email).first<Account>())!;
      } else {
        account = await createAccount(env, { googleSub: google.sub, deviceId, email: google.email });
      }
    }
  } else {
    if (!isDev(env)) fail(401, "sign_in_needed", "Sign in with Google to send links.");
    account = await env.DB.prepare(`SELECT * FROM accounts WHERE device_id = ?1 AND google_sub IS NULL`).bind(deviceId).first<Account>();
    account ??= await createAccount(env, { googleSub: null, deviceId, email: null });
  }

  const token = randomToken();
  await env.DB.prepare(`INSERT INTO sessions (token_hash, account_id, created_at) VALUES (?1, ?2, ?3)`)
    .bind(await sha256Hex(token), account.id, now()).run();
  return c.json({ token, account: publicAccount(c, account) });
}

export function publicAccount(c: Context<AppEnv>, a: Account) {
  const origin = new URL(c.req.url).origin;
  return { id: a.id, plan: a.plan, email: a.email, inboundUrl: `${origin}/hooks/in/${a.inbound_token}` };
}

/** Requires `Authorization: Bearer <session token>` and applies the per-minute request limit. */
export const requireAccount: MiddlewareHandler<AppEnv> = async (c, next) => {
  const token = c.req.header("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!token) fail(401, "sign_in_needed", "Sign in to do that.");
  const account = await c.env.DB.prepare(
    `SELECT a.* FROM sessions s JOIN accounts a ON a.id = s.account_id WHERE s.token_hash = ?1`,
  ).bind(await sha256Hex(token)).first<Account>();
  if (!account) fail(401, "sign_in_needed", "Your sign-in has expired. Sign in again.");
  const perMinute = account.plan === "pro" ? 120 : 60;
  const minute = new Date().toISOString().slice(0, 16);
  if ((await bump(c.env, account.id, "rpm", minute)) > perMinute) {
    fail(429, "slow_down", "Too many requests. Wait a minute and try again.");
  }
  c.set("account", account);
  await next();
};
