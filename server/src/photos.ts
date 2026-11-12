import type { Context } from "hono";
import type { AppEnv, Env } from "./env";
import { LIMITS, limitsFor } from "./limits";
import { fail, isHash, now, sha256Hex } from "./util";

export const r2Key = (accountId: string, hash: string) => `p/${accountId}/${hash}`;

const JPEG = [0xff, 0xd8, 0xff];
const isJpeg = (b: Uint8Array) => JPEG.every((x, i) => b[i] === x);
const isWebp = (b: Uint8Array) =>
  b.length > 12 && String.fromCharCode(...b.slice(0, 4)) === "RIFF" && String.fromCharCode(...b.slice(8, 12)) === "WEBP";

export async function storedBytes(env: Env, accountId?: string): Promise<number> {
  const row = accountId
    ? await env.DB.prepare(`SELECT COALESCE(SUM(bytes), 0) AS n FROM photos WHERE account_id = ?1`).bind(accountId).first<{ n: number }>()
    : await env.DB.prepare(`SELECT COALESCE(SUM(bytes), 0) AS n FROM photos`).first<{ n: number }>();
  return row?.n ?? 0;
}

/** Which of these photos the account doesn't have yet. */
export async function missingHashes(env: Env, accountId: string, hashes: string[]): Promise<string[]> {
  if (hashes.length === 0) return [];
  const have = new Set<string>();
  for (let i = 0; i < hashes.length; i += 90) {
    const chunk = hashes.slice(i, i + 90);
    const rows = await env.DB.prepare(
      `SELECT hash FROM photos WHERE account_id = ?1 AND hash IN (${chunk.map((_, j) => `?${j + 2}`).join(",")})`,
    ).bind(accountId, ...chunk).all<{ hash: string }>();
    rows.results.forEach((r) => have.add(r.hash));
  }
  return [...new Set(hashes)].filter((h) => !have.has(h));
}

/**
 * PUT /v1/photos/:hash  (body: the JPEG or WebP)
 * The hash must match the bytes. Refused past the photo size, the account's storage, or (for free
 * accounts) the global brake on total storage.
 */
export async function uploadPhoto(c: Context<AppEnv>) {
  const account = c.get("account");
  const hash = c.req.param("hash");
  if (!isHash(hash)) fail(400, "bad_hash", "Photo name must be its SHA-256.");
  const declared = Number(c.req.header("content-length") ?? "0");
  if (declared > LIMITS.both.maxPhotoBytes) fail(413, "photo_too_big", "That photo is too big (1 MB max after resizing).");

  const body = new Uint8Array(await c.req.arrayBuffer());
  if (body.length === 0) fail(400, "empty", "The photo was empty.");
  if (body.length > LIMITS.both.maxPhotoBytes) fail(413, "photo_too_big", "That photo is too big (1 MB max after resizing).");
  const type = isJpeg(body) ? "image/jpeg" : isWebp(body) ? "image/webp" : null;
  if (!type) fail(415, "not_a_photo", "Only JPEG or WebP photos can be uploaded.");
  if ((await sha256Hex(body)) !== hash) fail(422, "hash_mismatch", "The photo didn't arrive whole. It'll retry.");

  const env = c.env;
  const existing = await env.DB.prepare(`SELECT 1 FROM photos WHERE account_id = ?1 AND hash = ?2`).bind(account.id, hash).first();
  if (existing) return c.json({ ok: true, stored: false });

  const used = await storedBytes(env, account.id);
  if (used + body.length > limitsFor(account.plan).storageBytes) {
    fail(507, "storage_full", account.plan === "pro"
      ? "Your 5 GB is full. Delete old galleries to make room."
      : "Your free storage is full. Close an old gallery, or go Pro.");
  }
  if (account.plan === "free" && (await storedBytes(env)) + body.length > Number(env.FREE_STORAGE_BRAKE_BYTES)) {
    fail(507, "busy", "Free uploads are paused for now. Try again later, or go Pro.");
  }

  await env.PHOTOS.put(r2Key(account.id, hash), body, {
    httpMetadata: { contentType: type, cacheControl: "public, max-age=31536000, immutable" },
  });
  await env.DB.prepare(
    `INSERT OR IGNORE INTO photos (account_id, hash, bytes, content_type, created_at) VALUES (?1, ?2, ?3, ?4, ?5)`,
  ).bind(account.id, hash, body.length, type, now()).run();
  return c.json({ ok: true, stored: true }, 201);
}

/** Deletes photos that no gallery shows and that are older than [graceMs]. Returns how many. */
export async function deleteUnreferenced(env: Env, graceMs: number, accountId?: string): Promise<number> {
  const cutoff = now() - graceMs;
  const rows = await env.DB.prepare(
    `SELECT p.account_id, p.hash FROM photos p
     WHERE p.created_at <= ?1 ${accountId ? "AND p.account_id = ?2" : ""}
       AND NOT EXISTS (SELECT 1 FROM gallery_photos gp JOIN galleries g ON g.id = gp.gallery_id
                       WHERE gp.hash = p.hash AND g.account_id = p.account_id)
     LIMIT 500`,
  ).bind(...(accountId ? [cutoff, accountId] : [cutoff])).all<{ account_id: string; hash: string }>();
  for (const r of rows.results) {
    await env.PHOTOS.delete(r2Key(r.account_id, r.hash));
    await env.DB.prepare(`DELETE FROM photos WHERE account_id = ?1 AND hash = ?2`).bind(r.account_id, r.hash).run();
  }
  return rows.results.length;
}
