import type { Context } from "hono";
import type { AppEnv, Env } from "./env";
import { isDev } from "./env";
import { limitsFor } from "./limits";
import { fail, hmacSha256Hex, now, randomToken } from "./util";

export const EVENTS = ["gallery.published", "gallery.updated", "gallery.viewed", "ping"] as const;
type Event = (typeof EVENTS)[number];

/** Retry gaps after a failed delivery; after the last one it's given up (≈ 24 h in total). */
const BACKOFF_MS = [60_000, 5 * 60_000, 30 * 60_000, 2 * 3600_000, 6 * 3600_000, 15 * 3600_000];

interface WebhookRow { id: string; account_id: string; url: string; secret: string; events: string }
interface DeliveryRow { id: string; webhook_id: string; account_id: string; event: string; payload: string; attempts: number }

function checkUrl(env: Env, raw: unknown): string {
  if (typeof raw !== "string") fail(400, "bad_url", "Give the full web address, starting with https://");
  let u: URL;
  try { u = new URL(raw); } catch { fail(400, "bad_url", "That isn't a web address."); }
  if (u.protocol !== "https:" && !(isDev(env) && u.protocol === "http:")) fail(400, "bad_url", "Webhook addresses must start with https://");
  if (/^(localhost|127\.|10\.|192\.168\.|169\.254\.|\[?::1)/.test(u.hostname) && !isDev(env)) fail(400, "bad_url", "That address isn't reachable from the internet.");
  return u.toString();
}

/** Records an event for every webhook that wants it, and tries to deliver straight away. */
export async function emit(env: Env, accountId: string, event: Event, data: unknown): Promise<void> {
  const hooks = await env.DB.prepare(`SELECT * FROM webhooks WHERE account_id = ?1`).bind(accountId).all<WebhookRow>();
  const wanted = hooks.results.filter((h) => event === "ping" || h.events.split(",").includes(event) || h.events === "*");
  for (const h of wanted) {
    const id = crypto.randomUUID();
    const payload = JSON.stringify({ id, event, createdAt: new Date().toISOString(), data });
    await env.DB.prepare(
      `INSERT INTO webhook_deliveries (id, webhook_id, account_id, event, payload, status, attempts, next_attempt_at, created_at)
       VALUES (?1, ?2, ?3, ?4, ?5, 'pending', 0, ?6, ?6)`,
    ).bind(id, h.id, accountId, event, payload, now()).run();
    await deliver(env, { id, webhook_id: h.id, account_id: accountId, event, payload, attempts: 0 }, h);
  }
}

/** Headers: X-Pairshot-Event, X-Pairshot-Delivery, X-Pairshot-Signature: t=<unix>,v1=<hmac-sha256(secret, "t.body")> */
export async function sign(secret: string, body: string, t = Math.floor(now() / 1000)): Promise<string> {
  return `t=${t},v1=${await hmacSha256Hex(secret, `${t}.${body}`)}`;
}

async function deliver(env: Env, d: DeliveryRow, hook?: WebhookRow): Promise<boolean> {
  hook ??= (await env.DB.prepare(`SELECT * FROM webhooks WHERE id = ?1`).bind(d.webhook_id).first<WebhookRow>()) ?? undefined;
  if (!hook) return false;
  let status: number | null = null;
  let error: string | null = null;
  try {
    const res = await fetch(hook.url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "user-agent": "Pairshot-Webhooks/1",
        "x-pairshot-event": d.event,
        "x-pairshot-delivery": d.id,
        "x-pairshot-signature": await sign(hook.secret, d.payload),
      },
      body: d.payload,
      signal: AbortSignal.timeout(10_000),
    });
    status = res.status;
    if (!res.ok) error = `HTTP ${res.status}`;
  } catch (e) {
    error = e instanceof Error ? e.message.slice(0, 200) : "network error";
  }
  const attempts = d.attempts + 1;
  const ok = error === null;
  const gap = BACKOFF_MS[attempts - 1];
  await env.DB.prepare(
    `UPDATE webhook_deliveries SET status = ?2, attempts = ?3, next_attempt_at = ?4, last_status = ?5, last_error = ?6 WHERE id = ?1`,
  ).bind(d.id, ok ? "ok" : gap === undefined ? "failed" : "pending", attempts, ok || gap === undefined ? null : now() + gap, status, error).run();
  return ok;
}

/** Cron: retries deliveries that are due. */
export async function retryDue(env: Env): Promise<number> {
  const due = await env.DB.prepare(
    `SELECT * FROM webhook_deliveries WHERE status = 'pending' AND next_attempt_at <= ?1 ORDER BY next_attempt_at LIMIT 50`,
  ).bind(now()).all<DeliveryRow>();
  for (const d of due.results) await deliver(env, d);
  return due.results.length;
}

// ---- Routes (signed-in phone) -------------------------------------------------------------------

export async function listHooks(c: Context<AppEnv>) {
  const a = c.get("account");
  const hooks = await c.env.DB.prepare(`SELECT id, url, events, created_at FROM webhooks WHERE account_id = ?1 ORDER BY created_at`).bind(a.id).all();
  const deliveries = await c.env.DB.prepare(
    `SELECT id, webhook_id, event, status, attempts, last_status, last_error, created_at FROM webhook_deliveries
     WHERE account_id = ?1 ORDER BY created_at DESC LIMIT 50`,
  ).bind(a.id).all();
  return c.json({ webhooks: hooks.results, deliveries: deliveries.results });
}

export async function addHook(c: Context<AppEnv>) {
  const a = c.get("account");
  const max = limitsFor(a.plan).webhooks;
  if (max === 0) fail(402, "pro_only", "Connecting your CRM is part of Pro.");
  const count = await c.env.DB.prepare(`SELECT COUNT(*) AS n FROM webhooks WHERE account_id = ?1`).bind(a.id).first<{ n: number }>();
  if ((count?.n ?? 0) >= max) fail(422, "too_many_webhooks", `You can have ${max} webhooks.`);
  const body = await c.req.json<{ url?: unknown; events?: unknown }>().catch(() => ({}) as Record<string, unknown>);
  const url = checkUrl(c.env, body.url);
  const events = Array.isArray(body.events) && body.events.length
    ? body.events.filter((e): e is string => (EVENTS as readonly string[]).includes(e as string))
    : ["gallery.published", "gallery.updated", "gallery.viewed"];
  if (!events.length) fail(400, "bad_events", "Pick at least one event.");
  const id = crypto.randomUUID();
  const secret = `whsec_${randomToken(24)}`;
  await c.env.DB.prepare(`INSERT INTO webhooks (id, account_id, url, secret, events, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)`)
    .bind(id, a.id, url, secret, events.join(","), now()).run();
  return c.json({ id, url, events, secret }, 201);
}

export async function deleteHook(c: Context<AppEnv>) {
  const a = c.get("account");
  await c.env.DB.prepare(`DELETE FROM webhooks WHERE id = ?1 AND account_id = ?2`).bind(c.req.param("id"), a.id).run();
  return c.json({ ok: true });
}

/** POST /v1/webhooks/:id/test: sends a "ping" so the user can check Zapier/Make receives it. */
export async function testHook(c: Context<AppEnv>) {
  const a = c.get("account");
  const hook = await c.env.DB.prepare(`SELECT * FROM webhooks WHERE id = ?1 AND account_id = ?2`).bind(c.req.param("id"), a.id).first<WebhookRow>();
  if (!hook) fail(404, "not_found", "No such webhook.");
  const id = crypto.randomUUID();
  const payload = JSON.stringify({ id, event: "ping", createdAt: new Date().toISOString(), data: { message: "Hello from Pairshot" } });
  await c.env.DB.prepare(
    `INSERT INTO webhook_deliveries (id, webhook_id, account_id, event, payload, status, attempts, created_at) VALUES (?1, ?2, ?3, 'ping', ?4, 'pending', 0, ?5)`,
  ).bind(id, hook.id, a.id, payload, now()).run();
  const ok = await deliver(c.env, { id, webhook_id: hook.id, account_id: a.id, event: "ping", payload, attempts: 0 }, hook);
  return c.json({ ok });
}
