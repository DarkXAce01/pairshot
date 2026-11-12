import { HTTPException } from "hono/http-exception";

export const now = () => Date.now();

export function randomToken(bytes = 32): string {
  const b = new Uint8Array(bytes);
  crypto.getRandomValues(b);
  return base64url(b);
}

export function base64url(b: Uint8Array): string {
  let s = "";
  for (const x of b) s += String.fromCharCode(x);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Short, unguessable gallery slug: 12 characters from 62 ≈ 71 bits. */
export function slug(len = 12): string {
  const alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/l/I lookalikes
  const out: string[] = [];
  const limit = alphabet.length * Math.floor(256 / alphabet.length); // reject the tail so every letter is equally likely
  while (out.length < len) {
    const b = new Uint8Array(len * 2);
    crypto.getRandomValues(b);
    for (const v of b) if (v < limit && out.length < len) out.push(alphabet[v % alphabet.length]);
  }
  return out.join("");
}

export async function sha256Hex(data: ArrayBuffer | Uint8Array | string): Promise<string> {
  const buf = typeof data === "string" ? new TextEncoder().encode(data) : data;
  const h = await crypto.subtle.digest("SHA-256", buf);
  return [...new Uint8Array(h)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

export async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return [...new Uint8Array(sig)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

/** A plain-English refusal the app can show as it is. */
export function fail(status: 400 | 401 | 402 | 403 | 404 | 409 | 410 | 413 | 415 | 422 | 429 | 501 | 507, code: string, message: string): never {
  throw new HTTPException(status, { res: Response.json({ error: code, message }, { status }) });
}

export const isUuidish = (s: unknown): s is string => typeof s === "string" && /^[A-Za-z0-9-]{8,64}$/.test(s);
export const isHash = (s: unknown): s is string => typeof s === "string" && /^[0-9a-f]{64}$/.test(s);

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}
