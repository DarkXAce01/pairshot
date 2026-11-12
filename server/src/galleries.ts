import type { Context } from "hono";
import { bump, read } from "./counters";
import type { Account, AppEnv, Env } from "./env";
import { LIMITS, dayKey, limitsFor, monthKey } from "./limits";
import { parseManifest } from "./manifest";
import { deleteUnreferenced, missingHashes } from "./photos";
import { fail, isUuidish, now, slug } from "./util";
import { emit } from "./webhooks";

export interface GalleryRow {
  id: string;
  account_id: string;
  job_id: string;
  external_id: string | null;
  slug: string;
  manifest: string | null;
  job_json: string | null;
  lock_key: string;
  created_at: number;
  published_at: number | null;
  updated_at: number | null;
  expires_at: number | null;
  revoked_at: number | null;
  first_viewed_at: number | null;
  view_count: number;
}

interface SendBody {
  jobId?: unknown;
  externalId?: unknown;
  job?: { clientName?: unknown; clientPhone?: unknown; clientEmail?: unknown; address?: unknown; service?: unknown };
  manifest?: unknown;
}

export const galleryUrl = (c: Context<AppEnv>, s: string) => `${new URL(c.req.url).origin}/g/${s}`;

export function galleryState(g: GalleryRow, at = now()): "live" | "expired" | "revoked" | "draft" {
  if (g.revoked_at) return "revoked";
  if (!g.published_at) return "draft";
  if (g.expires_at && g.expires_at <= at) return "expired";
  return "live";
}

export function publicGallery(c: Context<AppEnv>, g: GalleryRow) {
  return {
    id: g.id,
    jobId: g.job_id,
    externalId: g.external_id,
    url: galleryUrl(c, g.slug),
    state: galleryState(g),
    publishedAt: g.published_at,
    updatedAt: g.updated_at,
    expiresAt: g.expires_at,
    firstViewedAt: g.first_viewed_at,
    viewCount: g.view_count,
  };
}

const lockKey = (title: string, place: string | undefined) => `${title}|${place ?? ""}`.toLowerCase().replace(/\s+/g, " ");

async function load(env: Env, id: string, accountId: string): Promise<GalleryRow | null> {
  const g = await env.DB.prepare(`SELECT * FROM galleries WHERE id = ?1`).bind(id).first<GalleryRow>();
  if (g && g.account_id !== accountId) fail(404, "not_found", "No such gallery.");
  return g;
}

/** Everything that decides whether this send/update is allowed. Throws a friendly refusal if not. */
async function checkAllowed(env: Env, account: Account, existing: GalleryRow | null, jobId: string, lock: string, hashes: string[], photoCount: number) {
  const lim = limitsFor(account.plan);
  const at = now();
  if (photoCount > lim.photosPerGallery) {
    fail(422, "too_many_photos", `A gallery can show ${lim.photosPerGallery} photos on your plan. Hide a few pairs${account.plan === "free" ? ", or go Pro" : ""}.`);
  }
  if (!existing) {
    if ((await read(env, account.id, "galleries", monthKey(at))) >= lim.galleriesPerMonth) {
      fail(402, "monthly_limit", `You've sent your ${lim.galleriesPerMonth} free galleries this month. Go Pro for unlimited, or wait until next month.`);
    }
    if ((await read(env, account.id, "galleries", dayKey(at))) >= lim.galleriesPerDay) {
      fail(429, "daily_limit", "That's a lot of new galleries for one day. Try again tomorrow.");
    }
    return;
  }
  if (existing.job_id !== jobId) fail(409, "wrong_job", "This gallery belongs to a different job.");
  const state = galleryState(existing, at);
  if (state === "revoked") fail(410, "closed", "This gallery was closed. Send a new one.");
  if (state === "expired") fail(410, "expired", "This gallery has expired. Send a new one.");
  if (existing.published_at && at - existing.published_at > lim.editWindowMs) {
    fail(403, "edit_window", "Free galleries can be changed for 3 days after sending. Go Pro to change them any time.");
  }
  if (account.plan === "free" && existing.lock_key !== lock) {
    fail(403, "locked", "The job's name and address can't change after sending on the free plan.");
  }
  if ((await read(env, existing.id, "updates", dayKey(at))) >= LIMITS.both.updatesPerGalleryPerDay) {
    fail(429, "too_many_updates", "This gallery has been updated a lot today. Try again tomorrow.");
  }
  const history = await env.DB.prepare(`SELECT hash FROM gallery_history WHERE gallery_id = ?1`).bind(existing.id).all<{ hash: string }>();
  const ever = new Set(history.results.map((r) => r.hash));
  hashes.forEach((h) => ever.add(h));
  if (ever.size > Math.floor(lim.photosPerGallery * LIMITS.both.historyFactor)) {
    fail(403, "reused", "This gallery has had too many different photos. Send the new job as its own gallery.");
  }
}

async function parseSend(c: Context<AppEnv>) {
  const id = c.req.param("id")!;
  if (!isUuidish(id)) fail(400, "bad_id", "Odd gallery id.");
  const body = await c.req.json<SendBody>().catch(() => fail(400, "bad_json", "That wasn't valid JSON."));
  if (!isUuidish(body.jobId)) fail(400, "bad_job", "Missing job id.");
  const parsed = parseManifest(body.manifest);
  return { id, body, jobId: body.jobId as string, ...parsed, lock: lockKey(parsed.manifest.job.title, parsed.manifest.job.place) };
}

/** POST /v1/galleries/:id/prepare: checks the limits and says which photos still need uploading. */
export async function prepare(c: Context<AppEnv>) {
  const account = c.get("account");
  const { id, jobId, hashes, photoCount, lock } = await parseSend(c);
  const existing = await load(c.env, id, account.id);
  await checkAllowed(c.env, account, existing, jobId, lock, hashes, photoCount);
  return c.json({ missing: await missingHashes(c.env, account.id, hashes) });
}

/** POST /v1/galleries/:id/publish: puts the gallery live (first send) or updates it (same link). */
export async function publish(c: Context<AppEnv>) {
  const account = c.get("account");
  const env = c.env;
  const { id, body, jobId, manifest, hashes, photoCount, lock } = await parseSend(c);
  const existing = await load(env, id, account.id);
  await checkAllowed(env, account, existing, jobId, lock, hashes, photoCount);
  const missing = await missingHashes(env, account.id, hashes);
  if (missing.length) fail(409, "photos_missing", `${missing.length} photos haven't uploaded yet.`);

  manifest.badge = account.plan !== "pro";
  const job = body.job ?? {};
  const s = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim().slice(0, 200) : null);
  const jobJson = JSON.stringify({
    clientName: s(job.clientName), clientPhone: s(job.clientPhone), clientEmail: s(job.clientEmail),
    address: s(job.address), service: s(job.service),
  });
  const externalId = s(body.externalId);
  const at = now();
  const lim = limitsFor(account.plan);

  let gallery: GalleryRow;
  if (!existing) {
    await bump(env, account.id, "galleries", monthKey(at));
    await bump(env, account.id, "galleries", dayKey(at));
    const expires = Number.isFinite(lim.lifetimeMs) ? at + lim.lifetimeMs : null;
    gallery = (await env.DB.prepare(
      `INSERT INTO galleries (id, account_id, job_id, external_id, slug, manifest, job_json, lock_key, created_at, published_at, updated_at, expires_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?9, ?9, ?10) RETURNING *`,
    ).bind(id, account.id, jobId, externalId, slug(), JSON.stringify(manifest), jobJson, lock, at, expires).first<GalleryRow>())!;
  } else {
    await bump(env, existing.id, "updates", dayKey(at));
    gallery = (await env.DB.prepare(
      `UPDATE galleries SET manifest = ?2, job_json = ?3, external_id = COALESCE(?4, external_id), updated_at = ?5 WHERE id = ?1 RETURNING *`,
    ).bind(id, JSON.stringify(manifest), jobJson, externalId, at).first<GalleryRow>())!;
  }

  const stmts = [env.DB.prepare(`DELETE FROM gallery_photos WHERE gallery_id = ?1`).bind(id)];
  for (const h of hashes) {
    stmts.push(env.DB.prepare(`INSERT OR IGNORE INTO gallery_photos (gallery_id, hash) VALUES (?1, ?2)`).bind(id, h));
    stmts.push(env.DB.prepare(`INSERT OR IGNORE INTO gallery_history (gallery_id, hash) VALUES (?1, ?2)`).bind(id, h));
  }
  await env.DB.batch(stmts);

  const result = publicGallery(c, gallery);
  c.executionCtx.waitUntil(emit(env, account.id, existing ? "gallery.updated" : "gallery.published", { gallery: result, job: { id: jobId, externalId, ...JSON.parse(jobJson) } }));
  return c.json(result, existing ? 200 : 201);
}

/** GET /v1/galleries: the account's galleries, for "Seen" and status on the phone. */
export async function list(c: Context<AppEnv>) {
  const account = c.get("account");
  const rows = await c.env.DB.prepare(`SELECT * FROM galleries WHERE account_id = ?1 ORDER BY created_at DESC LIMIT 500`)
    .bind(account.id).all<GalleryRow>();
  return c.json({ galleries: rows.results.map((g) => publicGallery(c, g)) });
}

/** DELETE /v1/galleries/:id: closes the link. Its photos are deleted (unless another gallery uses them). */
export async function revoke(c: Context<AppEnv>) {
  const account = c.get("account");
  const g = await load(c.env, c.req.param("id")!, account.id);
  if (!g) fail(404, "not_found", "No such gallery.");
  await c.env.DB.batch([
    c.env.DB.prepare(`UPDATE galleries SET revoked_at = ?2 WHERE id = ?1 AND revoked_at IS NULL`).bind(g.id, now()),
    c.env.DB.prepare(`DELETE FROM gallery_photos WHERE gallery_id = ?1`).bind(g.id),
  ]);
  await deleteUnreferenced(c.env, 0, account.id);
  return c.json({ ok: true });
}

/** GET /v1/usage: what's left on this plan. */
export async function usage(c: Context<AppEnv>) {
  const account = c.get("account");
  const lim = limitsFor(account.plan);
  const used = await c.env.DB.prepare(`SELECT COALESCE(SUM(bytes), 0) AS n FROM photos WHERE account_id = ?1`).bind(account.id).first<{ n: number }>();
  const month = await read(c.env, account.id, "galleries", monthKey(now()));
  const fin = (n: number) => (Number.isFinite(n) ? n : null);
  return c.json({
    plan: account.plan,
    galleriesThisMonth: month,
    galleriesPerMonth: fin(lim.galleriesPerMonth),
    photosPerGallery: lim.photosPerGallery,
    storageBytes: used?.n ?? 0,
    storageLimitBytes: lim.storageBytes,
    galleryLifetimeDays: Number.isFinite(lim.lifetimeMs) ? lim.lifetimeMs / 86_400_000 : null,
  });
}

/** Cron: galleries past their expiry lose their photos (the page then says it has expired). */
export async function expireGalleries(env: Env): Promise<number> {
  const rows = await env.DB.prepare(
    `SELECT g.id FROM galleries g WHERE g.expires_at IS NOT NULL AND g.expires_at <= ?1
       AND EXISTS (SELECT 1 FROM gallery_photos gp WHERE gp.gallery_id = g.id) LIMIT 200`,
  ).bind(now()).all<{ id: string }>();
  for (const r of rows.results) await env.DB.prepare(`DELETE FROM gallery_photos WHERE gallery_id = ?1`).bind(r.id).run();
  return rows.results.length;
}
