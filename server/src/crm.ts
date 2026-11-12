import type { Context } from "hono";
import { bump } from "./counters";
import type { Account, AppEnv, Env } from "./env";
import { dayKey, limitsFor } from "./limits";
import { fail, now, randomToken } from "./util";

/** A job as the phone receives it, whatever shape the CRM sent. */
export interface CrmJob {
  externalId: string;
  clientName: string | null;
  clientPhone: string | null;
  clientEmail: string | null;
  address: string | null;
  service: string | null;
  scheduledAt: number | null;
  notes: string | null;
  cancelled: boolean;
}

const ALIASES: Record<Exclude<keyof CrmJob, "cancelled">, string[]> = {
  externalId: ["external_id", "externalid", "job_id", "jobid", "job_number", "jobnumber", "id", "number", "visit_id", "appointment_id"],
  clientName: ["client_name", "clientname", "customer_name", "customername", "full_name", "fullname", "contact_name", "name", "client", "customer"],
  clientPhone: ["client_phone", "clientphone", "customer_phone", "phone", "phone_number", "mobile", "cell"],
  clientEmail: ["client_email", "clientemail", "customer_email", "email", "email_address"],
  address: ["address", "service_address", "property_address", "job_address", "location", "street", "address1"],
  service: ["service", "service_name", "title", "job_title", "job_type", "service_type", "description", "summary"],
  scheduledAt: ["scheduled_at", "scheduledat", "scheduled_start", "start_at", "start", "start_time", "starttime", "date", "appointment", "visit_date"],
  notes: ["notes", "note", "instructions", "comments"],
};

/** Snake-cases a key: "jobType" / "Service Address" / "client.first-name" → "job_type" / "service_address" / "client_first_name". */
const keyOf = (k: string) => k.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase().replace(/[\s.-]+/g, "_");

/**
 * Flattens nested objects: {client: {first_name: "x"}} → "client_first_name", and also the bare
 * inner name "first_name" — but a bare inner name never wins over a field the CRM sent at the top.
 */
function flatten(input: Record<string, unknown>): Map<string, unknown> {
  const out = new Map<string, unknown>();
  const bare: [string, unknown][] = [];
  const walk = (o: Record<string, unknown>, prefix: string, depth: number) => {
    for (const [k, v] of Object.entries(o)) {
      const key = prefix ? `${prefix}_${keyOf(k)}` : keyOf(k);
      if (v && typeof v === "object" && !Array.isArray(v)) {
        if (depth < 4) walk(v as Record<string, unknown>, key, depth + 1);
      } else {
        if (!out.has(key)) out.set(key, v);
        if (prefix) bare.push([keyOf(k), v]);
      }
    }
  };
  walk(input, "", 0);
  for (const [k, v] of bare) if (!out.has(k)) out.set(k, v);
  return out;
}

const text = (v: unknown, max = 300): string | null => {
  if (typeof v === "number") return String(v);
  return typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null;
};

export function parseTime(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return v < 1e11 ? v * 1000 : v; // seconds or millis
  if (typeof v !== "string" || !v.trim()) return null;
  if (/^\d{9,13}$/.test(v.trim())) return parseTime(Number(v.trim()));
  const t = Date.parse(v.trim());
  return Number.isNaN(t) ? null : t;
}

/** Turns whatever the CRM / Zapier / Make sent into a job. */
export function normalise(input: Record<string, unknown>): CrmJob {
  const f = flatten(input);
  const pick = (field: keyof typeof ALIASES) => {
    for (const a of ALIASES[field]) if (f.has(a) && f.get(a) !== null && f.get(a) !== "") return f.get(a);
    return undefined;
  };
  let clientName = text(pick("clientName"), 120);
  if (!clientName) {
    const first = text(f.get("first_name") ?? f.get("firstname") ?? f.get("client_first_name") ?? f.get("customer_first_name"), 60);
    const last = text(f.get("last_name") ?? f.get("lastname") ?? f.get("client_last_name") ?? f.get("customer_last_name"), 60);
    clientName = [first, last].filter(Boolean).join(" ") || null;
  }
  let address = text(pick("address"), 200);
  const city = text(f.get("city") ?? f.get("address_city"), 80);
  if (address && city && !address.toLowerCase().includes(city.toLowerCase())) address = `${address}, ${city}`;
  const status = String(f.get("status") ?? "").toLowerCase();
  const flag = f.get("cancelled") ?? f.get("canceled") ?? f.get("deleted");
  return {
    externalId: text(pick("externalId"), 120) ?? "",
    clientName,
    clientPhone: text(pick("clientPhone"), 40),
    clientEmail: text(pick("clientEmail"), 120),
    address,
    service: text(pick("service"), 120),
    scheduledAt: parseTime(pick("scheduledAt")),
    notes: text(pick("notes"), 1000),
    cancelled: flag === true || flag === "true" || flag === 1 || ["cancelled", "canceled", "deleted", "archived"].includes(status),
  };
}

async function readBody(c: Context<AppEnv>): Promise<Record<string, unknown>> {
  const type = c.req.header("content-type") ?? "";
  if (type.includes("application/x-www-form-urlencoded") || type.includes("multipart/form-data")) {
    const form = await c.req.parseBody();
    return Object.fromEntries(Object.entries(form).map(([k, v]) => [k, typeof v === "string" ? v : null]));
  }
  const json = await c.req.json<unknown>().catch(() => null);
  if (!json || typeof json !== "object" || Array.isArray(json)) fail(400, "bad_json", "Send the job as a JSON object.");
  return json as Record<string, unknown>;
}

/** Saves a job for the account (upsert by the CRM's id). Shared by the webhook and the API. */
export async function saveJob(env: Env, account: Account, input: Record<string, unknown>): Promise<CrmJob> {
  if (limitsFor(account.plan).crmJobsPerDay === 0) fail(402, "pro_only", "Connecting your CRM is part of Pairshot Pro.");
  const job = normalise(input);
  if (!job.externalId) fail(422, "external_id_needed", "Include the CRM's job id (external_id) so updates don't make duplicates.");
  if (!job.clientName && !job.cancelled) fail(422, "client_name_needed", "Include the client's name (client_name).");
  if ((await bump(env, account.id, "crm", dayKey(now()))) > limitsFor(account.plan).crmJobsPerDay) {
    fail(429, "daily_limit", "That's the most jobs the CRM can send in one day.");
  }
  // updated_at only ever goes up for an account, so the phone's "since" cursor never misses a job.
  await env.DB.prepare(
    `INSERT INTO crm_jobs (account_id, external_id, data, cancelled, updated_at)
     VALUES (?1, ?2, ?3, ?4, MAX(?5, COALESCE((SELECT MAX(updated_at) + 1 FROM crm_jobs WHERE account_id = ?1), 0)))
     ON CONFLICT (account_id, external_id) DO UPDATE SET data = excluded.data, cancelled = excluded.cancelled, updated_at = excluded.updated_at`,
  ).bind(account.id, job.externalId, JSON.stringify(job), job.cancelled ? 1 : 0, now()).run();
  return job;
}

/** POST /hooks/in/:token: the CRM (or Zapier / Make) sends a job here. No sign-in: the token is the secret. */
export async function inbound(c: Context<AppEnv>) {
  const token = c.req.param("token");
  const account = await c.env.DB.prepare(`SELECT * FROM accounts WHERE inbound_token = ?1`).bind(token).first<Account>();
  if (!account) fail(404, "unknown_hook", "This webhook address isn't valid any more. Copy the new one from the app.");
  const job = await saveJob(c.env, account, await readBody(c));
  return c.json({ ok: true, externalId: job.externalId, cancelled: job.cancelled });
}

/** GET /v1/crm/jobs?since=<cursor>: jobs the phone hasn't seen yet. */
export async function pull(c: Context<AppEnv>) {
  const account = c.get("account");
  const since = Number(c.req.query("since") ?? "0") || 0;
  const rows = await c.env.DB.prepare(
    `SELECT data, updated_at FROM crm_jobs WHERE account_id = ?1 AND updated_at > ?2 ORDER BY updated_at LIMIT 200`,
  ).bind(account.id, since).all<{ data: string; updated_at: number }>();
  const jobs = rows.results.map((r) => JSON.parse(r.data) as CrmJob);
  const cursor = rows.results.length ? rows.results[rows.results.length - 1].updated_at : since;
  return c.json({ jobs, cursor, more: rows.results.length === 200 });
}

/** POST /v1/crm/rotate: new secret webhook address (the old one stops working). */
export async function rotate(c: Context<AppEnv>) {
  const account = c.get("account");
  const token = randomToken(24);
  await c.env.DB.prepare(`UPDATE accounts SET inbound_token = ?2 WHERE id = ?1`).bind(account.id, token).run();
  return c.json({ inboundUrl: `${new URL(c.req.url).origin}/hooks/in/${token}` });
}
