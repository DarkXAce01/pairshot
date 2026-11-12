import type { Context, MiddlewareHandler } from "hono";
import { bump } from "./counters";
import { saveJob } from "./crm";
import type { Account, AppEnv } from "./env";
import { type GalleryRow, publicGallery } from "./galleries";
import { dayKey, limitsFor } from "./limits";
import { fail, now, randomToken, sha256Hex } from "./util";

// ---- Keys (managed from the phone) --------------------------------------------------------------

export async function createKey(c: Context<AppEnv>) {
  const a = c.get("account");
  if (limitsFor(a.plan).apiCallsPerDay === 0) fail(402, "pro_only", "The API is part of Pairshot Pro.");
  const key = `ps_${randomToken(24)}`;
  const id = crypto.randomUUID();
  await c.env.DB.prepare(`INSERT INTO api_keys (id, account_id, key_hash, prefix, created_at) VALUES (?1, ?2, ?3, ?4, ?5)`)
    .bind(id, a.id, await sha256Hex(key), key.slice(0, 7), now()).run();
  return c.json({ id, key, prefix: key.slice(0, 7) }, 201);
}

export async function listKeys(c: Context<AppEnv>) {
  const rows = await c.env.DB.prepare(`SELECT id, prefix, created_at, last_used_at FROM api_keys WHERE account_id = ?1 ORDER BY created_at`)
    .bind(c.get("account").id).all();
  return c.json({ keys: rows.results });
}

export async function deleteKey(c: Context<AppEnv>) {
  await c.env.DB.prepare(`DELETE FROM api_keys WHERE id = ?1 AND account_id = ?2`).bind(c.req.param("id"), c.get("account").id).run();
  return c.json({ ok: true });
}

// ---- The public API (Zapier, Make, scripts) ---------------------------------------------------

/** `Authorization: Bearer ps_…` or `X-Api-Key: ps_…`. Counts toward the daily API limit. */
export const requireKey: MiddlewareHandler<AppEnv> = async (c, next) => {
  const key = c.req.header("x-api-key") ?? c.req.header("authorization")?.match(/^Bearer (ps_.+)$/)?.[1];
  if (!key) fail(401, "api_key_needed", "Send your API key as X-Api-Key.");
  const hash = await sha256Hex(key);
  const account = await c.env.DB.prepare(
    `SELECT a.* FROM api_keys k JOIN accounts a ON a.id = k.account_id WHERE k.key_hash = ?1`,
  ).bind(hash).first<Account>();
  if (!account) fail(401, "bad_api_key", "That API key isn't valid.");
  const max = limitsFor(account.plan).apiCallsPerDay;
  if (max === 0) fail(402, "pro_only", "The API is part of Pairshot Pro.");
  if ((await bump(c.env, account.id, "api", dayKey(now()))) > max) fail(429, "daily_limit", `That's the ${max} API calls for today.`);
  c.executionCtx.waitUntil(c.env.DB.prepare(`UPDATE api_keys SET last_used_at = ?2 WHERE key_hash = ?1`).bind(hash, now()).run());
  c.set("account", account);
  await next();
};

/** GET /api/v1/me: for Zapier/Make's "test connection". */
export async function me(c: Context<AppEnv>) {
  const a = c.get("account");
  return c.json({ id: a.id, plan: a.plan, email: a.email });
}

function withJob(c: Context<AppEnv>, g: GalleryRow) {
  return { ...publicGallery(c, g), job: g.job_json ? JSON.parse(g.job_json) : null };
}

/** GET /api/v1/galleries?since=<ms>: newest first. Works as a polling trigger. */
export async function apiGalleries(c: Context<AppEnv>) {
  const since = Number(c.req.query("since") ?? "0") || 0;
  const rows = await c.env.DB.prepare(
    `SELECT * FROM galleries WHERE account_id = ?1 AND published_at IS NOT NULL AND COALESCE(updated_at, published_at) > ?2
     ORDER BY COALESCE(updated_at, published_at) DESC LIMIT 100`,
  ).bind(c.get("account").id, since).all<GalleryRow>();
  return c.json({ galleries: rows.results.map((g) => withJob(c, g)) });
}

/** GET /api/v1/galleries/lookup?external_id=JOB-1042: the gallery for a CRM job. */
export async function apiLookup(c: Context<AppEnv>) {
  const ext = c.req.query("external_id");
  if (!ext) fail(400, "external_id_needed", "Add ?external_id=<the CRM's job id>.");
  const g = await c.env.DB.prepare(
    `SELECT * FROM galleries WHERE account_id = ?1 AND external_id = ?2 ORDER BY created_at DESC LIMIT 1`,
  ).bind(c.get("account").id, ext).first<GalleryRow>();
  if (!g) fail(404, "not_found", "No gallery has been sent for that job yet.");
  return c.json(withJob(c, g));
}

/** POST /api/v1/jobs: same as the webhook address, but with an API key. */
export async function apiCreateJob(c: Context<AppEnv>) {
  const body = await c.req.json<Record<string, unknown>>().catch(() => fail(400, "bad_json", "Send the job as JSON."));
  const job = await saveJob(c.env, c.get("account"), body);
  return c.json({ ok: true, job }, 201);
}
