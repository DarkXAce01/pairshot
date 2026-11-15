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

function simplePage(title: string, body: string, wide = false): string {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${escapeHtml(title)}</title>
<style>body{margin:0;background:#F2EFE9;color:#1C1B19;font:17px/1.5 system-ui,sans-serif}main{max-width:560px;margin:0 auto;padding:72px 20px}
h1{font:400 34px/1.1 Georgia,serif;margin:0 0 12px}p,li{color:#55524C}a{color:#D4432A}
main.wide{max-width:720px}h2{font:400 26px/1.2 Georgia,serif;margin:40px 0 8px}h3{margin:24px 0 4px;font-size:17px}
code{font:14px ui-monospace,Menlo,monospace;background:#E6E1D8;padding:1px 5px;border-radius:4px}
ol,ul{padding-left:22px}li{margin:4px 0}table{border-collapse:collapse;width:100%;font-size:15px}td,th{border-bottom:1px solid #DDD7CC;padding:6px 4px;text-align:left;vertical-align:top}
@media(prefers-color-scheme:dark){body{background:#161514;color:#EDE9E2}p,li{color:#A39E95}a{color:#E5553A}code{background:#2A2826}td,th{border-color:#34322E}}</style></head>
<body><main${wide ? ' class="wide"' : ""}><h1>${escapeHtml(title)}</h1>${body}</main></body></html>`;
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

/** GET /help/crm: setup steps a business owner can follow from their phone. */
export function crmHelp(c: Context<AppEnv>) {
  return c.html(simplePage("Connect your CRM", `
<p>Pairshot Pro talks to your CRM both ways. You don't need any code: a free <a href="https://zapier.com">Zapier</a>
or <a href="https://www.make.com">Make</a> account does the joining. Everything you need to copy is in the app under
<b>Settings → Account &amp; CRM</b>.</p>

<h2>1. New jobs appear in Pairshot</h2>
<p>When a job is booked in your CRM, it shows up in your Pairshot job list with the client's name, address and time.</p>
<h3>With Zapier</h3>
<ol>
<li>In Pairshot, tap <b>Copy address</b> under <i>Jobs from your CRM</i>.</li>
<li>In Zapier, make a Zap. <b>Trigger:</b> your CRM's "new job", "new visit" or "new appointment" (Jobber, Housecall Pro,
Square, Google Calendar, Calendly…).</li>
<li><b>Action:</b> <i>Webhooks by Zapier → POST</i>. URL: paste the address. Payload type: <code>json</code>.</li>
<li>Under <i>Data</i>, add the fields you have. The names below work; others usually do too.</li>
<li>Press <b>Test</b>. Then open Pairshot and tap <b>Sync now</b>: the test job is in your list.</li>
</ol>
<table>
<tr><th>Name it</th><th>Put in</th></tr>
<tr><td><code>id</code></td><td>The job or visit ID (needed, so updates don't make a second job)</td></tr>
<tr><td><code>client_name</code></td><td>Client's name (needed)</td></tr>
<tr><td><code>phone</code>, <code>email</code></td><td>Client's phone and email (for the "Text" button)</td></tr>
<tr><td><code>address</code></td><td>Job address</td></tr>
<tr><td><code>service</code></td><td>What the job is, e.g. "House wash"</td></tr>
<tr><td><code>start_at</code></td><td>When it's booked</td></tr>
<tr><td><code>notes</code></td><td>Gate codes, instructions</td></tr>
<tr><td><code>status</code></td><td>Send "cancelled" to take a job off your list</td></tr>
</table>
<h3>With Make</h3>
<p>Your CRM's "watch new jobs" module → <i>HTTP → Make a request</i>: method POST, URL the address, body type JSON,
with the same fields.</p>
<h3>Straight from your CRM</h3>
<p>If your CRM can send webhooks itself (Housecall Pro, Square, Calendly and others), paste the address there.
Pairshot reads their usual fields without any mapping.</p>

<h2>2. Your CRM hears when you send a link</h2>
<p>Pairshot can tell your other tools when a before &amp; after link is sent, updated, or opened by the client for the
first time. Use it to add the link to the job, email it with the invoice, or ask for a review once they've seen it.</p>
<ol>
<li>In Zapier, make a Zap with <b>Trigger:</b> <i>Webhooks by Zapier → Catch Hook</i>. Copy the address it gives you.</li>
<li>In Pairshot, under <i>Webhooks</i>, paste it and tap <b>Add webhook</b>, then <b>Send a test</b>.</li>
<li>Back in Zapier, <b>Test trigger</b>: you'll see the test. Add a <i>Filter</i> on <code>event</code>:
<code>gallery.published</code> (sent), <code>gallery.updated</code> or <code>gallery.viewed</code> (the client opened it).</li>
<li>Add your action, e.g. <i>Jobber → Create note</i> on job <code>data job externalId</code> with
"Before &amp; after: <code>data gallery url</code>".</li>
</ol>
<p>Each message carries the link, the job (with your CRM's job ID) and the client's details.</p>

<h2>Ideas that work well</h2>
<ul>
<li><b>Review request:</b> on <code>gallery.viewed</code>, wait 2 hours, then text the client your Google review link.</li>
<li><b>Invoice with photos:</b> on <code>gallery.published</code>, add the link to the job so it goes out with the invoice.</li>
<li><b>Team chat:</b> post every sent link to Slack or WhatsApp so the office sees the work.</li>
</ul>

<h2>If something doesn't arrive</h2>
<ul>
<li>Jobs: check Zapier's Zap history. Pairshot answers with what's missing, e.g. "Include the client's name".</li>
<li>Webhooks: the app shows the last deliveries. Pairshot retries for a day if your Zap was off.</li>
<li>Someone else got your address? Tap <b>New address…</b> and update your Zap.</li>
</ul>
<p>Up to 200 jobs a day come in, and up to 5 webhooks. Both need Pro.</p>`, true));
}

export function deleteInfo(c: Context<AppEnv>) {
  return c.html(simplePage("Delete your data", `
<p>In the app: <b>Settings → Delete my cloud data</b>. That deletes your account, every gallery and every
uploaded photo at once. Photos on your phone stay on your phone.</p>
<p>Lost your phone? Email the studio from the address you signed in with and we'll delete it for you.</p>`));
}

export type { Env };
