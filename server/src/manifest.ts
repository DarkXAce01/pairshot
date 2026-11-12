import { fail, isHash } from "./util";

/** The gallery as the phone sends it. Image `src` is the photo's SHA-256; the server turns it into a URL. */
export interface Img { src: string; w: number; h: number }
export interface PairItem { id: string; label?: string; before: Img; after: Img }
export interface Section { label?: string; pairs: PairItem[] }
export interface Manifest {
  v: 1;
  business: { name: string; phone?: string; reviewUrl?: string; bookingUrl?: string; accent?: string };
  job: { title: string; place?: string; date?: string };
  cover: PairItem | null;
  sections: Section[];
  badge: boolean;
}

const str = (v: unknown, max: number): string | undefined =>
  typeof v === "string" && v.trim() ? v.trim().slice(0, max) : undefined;

const url = (v: unknown): string | undefined => {
  const s = str(v, 500);
  if (!s) return undefined;
  try {
    const u = new URL(s);
    return u.protocol === "https:" || u.protocol === "http:" ? u.toString() : undefined;
  } catch {
    return undefined;
  }
};

function img(v: unknown): Img {
  const o = v as Record<string, unknown> | null;
  if (!o || !isHash(o.src)) fail(400, "bad_manifest", "A photo in the gallery is missing.");
  const w = Number(o.w), h = Number(o.h);
  if (!(w > 0 && h > 0 && w < 20000 && h < 20000)) fail(400, "bad_manifest", "A photo has an odd size.");
  return { src: o.src, w: Math.round(w), h: Math.round(h) };
}

function pair(v: unknown): PairItem {
  const o = v as Record<string, unknown> | null;
  if (!o || typeof o.id !== "string") fail(400, "bad_manifest", "A pair in the gallery is broken.");
  return { id: o.id.slice(0, 64), label: str(o.label, 80), before: img(o.before), after: img(o.after) };
}

/** Checks and cleans a manifest from the phone. Unknown fields are dropped; text is length-capped. */
export function parseManifest(raw: unknown): { manifest: Manifest; hashes: string[]; photoCount: number } {
  const o = raw as Record<string, unknown> | null;
  if (!o || typeof o !== "object") fail(400, "bad_manifest", "The gallery data is missing.");
  const b = (o.business ?? {}) as Record<string, unknown>;
  const j = (o.job ?? {}) as Record<string, unknown>;
  const accent = str(b.accent, 7);
  const sections = Array.isArray(o.sections) ? o.sections : [];
  if (sections.length > 100) fail(400, "bad_manifest", "Too many sections.");
  const manifest: Manifest = {
    v: 1,
    business: {
      name: str(b.name, 120) ?? "",
      phone: str(b.phone, 40),
      reviewUrl: url(b.reviewUrl),
      bookingUrl: url(b.bookingUrl),
      accent: accent && /^#[0-9a-fA-F]{6}$/.test(accent) ? accent : undefined,
    },
    job: { title: str(j.title, 120) ?? "Your before & after", place: str(j.place, 120), date: str(j.date, 40) },
    cover: o.cover ? pair(o.cover) : null,
    sections: sections.map((s) => {
      const so = s as Record<string, unknown>;
      return { label: str(so.label, 80), pairs: (Array.isArray(so.pairs) ? so.pairs : []).map(pair) };
    }).filter((s) => s.pairs.length > 0),
    badge: true,
  };
  if (!manifest.cover) fail(400, "nothing_to_send", "There are no finished pairs to send yet.");
  const all = [manifest.cover, ...manifest.sections.flatMap((s) => s.pairs)];
  const hashes = [...new Set(all.flatMap((p) => [p.before.src, p.after.src]))];
  return { manifest, hashes, photoCount: all.length * 2 };
}

/** Swaps photo hashes for real URLs, for the page. */
export function withUrls(m: Manifest, toUrl: (hash: string) => string): Manifest {
  const fix = (p: PairItem): PairItem => ({ ...p, before: { ...p.before, src: toUrl(p.before.src) }, after: { ...p.after, src: toUrl(p.after.src) } });
  return { ...m, cover: m.cover && fix(m.cover), sections: m.sections.map((s) => ({ ...s, pairs: s.pairs.map(fix) })) };
}
