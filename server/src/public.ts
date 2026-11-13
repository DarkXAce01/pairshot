import type { Context } from "hono";
import template from "../../shared/gallery/gallery.html";
import type { AppEnv, Env } from "./env";
import { type GalleryRow, galleryState, publicGallery } from "./galleries";
import { type Manifest, withUrls } from "./manifest";
import { r2Key } from "./photos";
import { bump } from "./counters";
import { dayKey, LIMITS } from "./limits";
import { escapeHtml, isHash, now } from "./util";
import { photoName, type ZipEntry, zipStream } from "./zip";
import { emit } from "./webhooks";

/** Link previewers (SMS, WhatsApp, Slack…) fetch the page too; they must not count as the client. */
const BOT = /bot|crawl|spider|preview|facebookexternalhit|whatsapp|slack|telegram|discord|skype|embedly|vkshare|pinterest|google-?inspectiontool|headless|curl|wget|python-requests|okhttp/i;

let scriptHash: string | undefined;
async function inlineScriptHash(): Promise<string> {
  if (scriptHash) return scriptHash;
  const m = template.match(/<script>([\s\S]*?)<\/script>/);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(m ? m[1] : ""));
  scriptHash = `'sha256-${btoa(String.fromCharCode(...new Uint8Array(digest)))}'`;
  return scriptHash;
}

async function headers(extra: Record<string, string> = {}): Promise<Record<string, string>> {
  return {
    "content-type": "text/html; charset=utf-8",
    "x-robots-tag": "noindex, nofollow",
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
    "content-security-policy": `default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src ${await inlineScriptHash()}; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
    ...extra,
  };
}

function simplePage(title: string, body: string): string {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${escapeHtml(title)}</title>
<style>body{margin:0;background:#F2EFE9;color:#1C1B19;font:17px/1.5 system-ui,sans-serif}main{max-width:560px;margin:0 auto;padding:72px 20px}
h1{font:400 34px/1.1 Georgia,serif;margin:0 0 12px}p{color:#55524C}a{color:#D4432A}
@media(prefers-color-scheme:dark){body{background:#161514;color:#EDE9E2}p{color:#A39E95}a{color:#E5553A}}</style></head>
<body><main><h1>${escapeHtml(title)}</h1>${body}</main></body></html>`;
}

/** Puts the manifest into the shared template (same as the app's preview). */
export function render(manifest: Manifest, meta: string): string {
  const safe = JSON.stringify(manifest).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
  return template.replace("/*DATA*/", safe).replace("<!--META-->", meta);
}

/** GET /g/:slug: the client's page. */
export async function galleryPage(c: Context<AppEnv>) {
  const env = c.env;
  const g = await env.DB.prepare(`SELECT * FROM galleries WHERE slug = ?1`).bind(c.req.param("slug")).first<GalleryRow>();
  if (!g || !g.manifest) {
    return c.html(simplePage("Nothing here", "<p>This link doesn't lead to a gallery. Check it was copied in full.</p>"), 404, await headers());
  }
  const manifest = JSON.parse(g.manifest) as Manifest;
  const state = galleryState(g);
  if (state !== "live") {
    const who = manifest.business.name ? escapeHtml(manifest.business.name) : "the business that sent it";
    return c.html(
      simplePage("This gallery has closed", `<p>These before and after photos aren't available any more. Ask ${who} for a new link.</p>`),
      410, await headers(),
    );
  }

  const origin = new URL(c.req.url).origin;
  const page = { ...withUrls(manifest, (h) => `${origin}/i/${g.account_id}/${h}`), download: `${origin}/g/${g.slug}/photos.zip` };
  const title = `${page.job.title}${page.business.name ? ` · ${page.business.name}` : ""}`;
  const description = page.business.name ? `Before & after photos from ${page.business.name}.` : "Before & after photos.";
  const cover = page.cover?.after.src ?? "";
  const meta = [
    `<title>${escapeHtml(title)}</title>`,
    `<meta property="og:title" content="${escapeHtml(title)}">`,
    `<meta property="og:description" content="${escapeHtml(description)}">`,
    `<meta property="og:type" content="website">`,
    cover && `<meta property="og:image" content="${escapeHtml(cover)}">`,
    `<meta name="twitter:card" content="summary_large_image">`,
  ].filter(Boolean).join("\n");

  const ua = c.req.header("user-agent") ?? "";
  if (c.req.method === "GET" && !BOT.test(ua)) {
    const first = !g.first_viewed_at;
    c.executionCtx.waitUntil((async () => {
      await env.DB.prepare(
        `UPDATE galleries SET view_count = view_count + 1, first_viewed_at = COALESCE(first_viewed_at, ?2) WHERE id = ?1`,
      ).bind(g.id, now()).run();
      if (first) {
        const fresh = (await env.DB.prepare(`SELECT * FROM galleries WHERE id = ?1`).bind(g.id).first<GalleryRow>())!;
        await emit(env, g.account_id, "gallery.viewed", {
          gallery: publicGallery(c, fresh),
          job: { id: g.job_id, externalId: g.external_id, ...(g.job_json ? JSON.parse(g.job_json) : {}) },
        });
      }
    })());
  }
  return c.html(render(page, meta), 200, await headers({ "cache-control": "no-store" }));
}

/** GET /g/:slug/photos.zip: every photo in the gallery, named by area, for the client to keep. */
export async function galleryZip(c: Context<AppEnv>) {
  const env = c.env;
  const g = await env.DB.prepare(`SELECT * FROM galleries WHERE slug = ?1`).bind(c.req.param("slug")).first<GalleryRow>();
  if (!g || !g.manifest || galleryState(g) !== "live") return c.notFound();
  const count = await bump(env, g.account_id, `zip:${g.id}`, dayKey(now()));
  if (count > LIMITS.both.zipsPerGalleryPerDay) {
    return c.text("This gallery has been downloaded a lot today. Try again tomorrow, or save the photos one by one.", 429);
  }
  const m = JSON.parse(g.manifest) as Manifest;
  const pairs = [m.cover, ...m.sections.flatMap((s) => s.pairs.map((p) => ({ ...p, label: p.label ?? s.label })))]
    .filter((p): p is NonNullable<typeof p> => !!p);
  const seen = new Set<string>();
  const entries: ZipEntry[] = [];
  pairs.forEach((p, i) => {
    for (const side of ["before", "after"] as const) {
      const hash = p[side].src;
      if (!isHash(hash) || seen.has(hash)) continue;
      seen.add(hash);
      entries.push({
        name: photoName(i + 1, p.label, side),
        open: async () => (await env.PHOTOS.get(r2Key(g.account_id, hash)))?.body ?? null,
      });
    }
  });
  const file = (m.job.title || "photos").replace(/[^A-Za-z0-9 _-]+/g, "").trim().replace(/\s+/g, "-").slice(0, 60) || "photos";
  return new Response(zipStream(entries), {
    headers: {
      "content-type": "application/zip",
      "content-disposition": `attachment; filename="${file}.zip"`,
      "cache-control": "no-store",
      "x-robots-tag": "noindex, nofollow",
      "x-content-type-options": "nosniff",
    },
  });
}

/** GET /i/:account/:hash: a photo, only while a live gallery shows it. */
export async function photo(c: Context<AppEnv>) {
  const { account, hash } = c.req.param();
  if (!isHash(hash)) return c.notFound();
  const live = await c.env.DB.prepare(
    `SELECT 1 FROM gallery_photos gp JOIN galleries g ON g.id = gp.gallery_id
     WHERE gp.hash = ?1 AND g.account_id = ?2 AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > ?3) LIMIT 1`,
  ).bind(hash, account, now()).first();
  if (!live) return c.notFound();
  const obj = await c.env.PHOTOS.get(r2Key(account, hash));
  if (!obj) return c.notFound();
  return new Response(obj.body, {
    headers: {
      "content-type": obj.httpMetadata?.contentType ?? "image/jpeg",
      "cache-control": "public, max-age=3600",
      etag: obj.httpEtag,
      "x-content-type-options": "nosniff",
    },
  });
}

export function home(c: Context<AppEnv>) {
  return c.html(simplePage("Pairshot", "<p>Before and after photos for service businesses, by Grease Pencil Studio.</p>"));
}

export function privacy(c: Context<AppEnv>) {
  return c.html(simplePage("Privacy", `
<p>Pairshot keeps your jobs and photos on your phone. Nothing leaves it until you send a client link.</p>
<p>When you send a link we store the photos in that gallery (resized, with location and camera details removed),
the job title and street you chose to show, your business name and buttons, and the client details you entered
so your own CRM webhooks can use them. Clients see only the gallery page.</p>
<p>Free galleries are deleted 14 days after sending. Closing a gallery deletes its photos straight away.
Pairshot shows no ads and has no third-party trackers.</p>
<p>To delete everything we store, see <a href="/delete-account">delete your data</a>.</p>`));
}

export function deleteInfo(c: Context<AppEnv>) {
  return c.html(simplePage("Delete your data", `
<p>In the app: <b>Settings → Delete my cloud data</b>. That deletes your account, every gallery and every
uploaded photo at once. Photos on your phone stay on your phone.</p>
<p>Lost your phone? Email the studio from the address you signed in with and we'll delete it for you.</p>`));
}

export type { Env };
