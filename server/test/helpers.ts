import { createExecutionContext, env, waitOnExecutionContext } from "cloudflare:test";
import worker from "../src/index";

export const BASE = "https://pairshot.test";

type Opts = { token?: string; body?: unknown; headers?: Record<string, string>; raw?: BodyInit; env?: Partial<Cloudflare.Env> };

/** Calls the worker directly and waits for its background work (webhooks, view counts). */
export async function call(method: string, path: string, o: Opts = {}): Promise<Response> {
  const headers: Record<string, string> = { ...(o.headers ?? {}) };
  if (o.token) headers.authorization = `Bearer ${o.token}`;
  let body: BodyInit | undefined = o.raw;
  if (o.body !== undefined) {
    body = JSON.stringify(o.body);
    headers["content-type"] ??= "application/json";
  }
  const ctx = createExecutionContext();
  const res = await worker.fetch(new Request(BASE + path, { method, headers, body }), { ...env, ...(o.env ?? {}) } as never, ctx);
  await waitOnExecutionContext(ctx);
  return res;
}

export async function json<T = any>(res: Response): Promise<T> {
  return (await res.json()) as T;
}

let n = 0;
export async function signIn(plan: "free" | "pro" = "free") {
  const deviceId = `device-${Date.now()}-${n++}-${Math.random().toString(36).slice(2)}`;
  const res = await call("POST", "/v1/auth", { body: { deviceId } });
  const { token, account } = await json(res);
  if (plan === "pro") await call("POST", "/v1/dev/plan", { token, body: { plan: "pro" } });
  return { token, account, deviceId };
}

export async function sha256(b: Uint8Array): Promise<string> {
  const h = await crypto.subtle.digest("SHA-256", b);
  return [...new Uint8Array(h)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

/** A tiny fake JPEG with unique bytes. */
export function jpeg(size = 64): Uint8Array {
  const b = new Uint8Array(size);
  for (let i = 0; i < size; i += 65536) crypto.getRandomValues(b.subarray(i, Math.min(size, i + 65536)));
  b[0] = 0xff; b[1] = 0xd8; b[2] = 0xff;
  return b;
}

export async function upload(token: string, bytes = jpeg()): Promise<string> {
  const hash = await sha256(bytes);
  const res = await call("PUT", `/v1/photos/${hash}`, { token, raw: bytes, headers: { "content-type": "image/jpeg" } });
  if (res.status >= 300) throw new Error(`upload failed ${res.status} ${await res.text()}`);
  return hash;
}

export interface Pair { id: string; before: string; after: string; label?: string }

export async function makePairs(token: string, count: number, label?: string): Promise<Pair[]> {
  const out: Pair[] = [];
  for (let i = 0; i < count; i++) out.push({ id: `p${i}-${crypto.randomUUID()}`, before: await upload(token), after: await upload(token), label });
  return out;
}

export function manifest(pairs: Pair[], over: Record<string, unknown> = {}) {
  const item = (p: Pair) => ({ id: p.id, label: p.label, before: { src: p.before, w: 3, h: 4 }, after: { src: p.after, w: 3, h: 4 } });
  const [cover, ...rest] = pairs;
  return {
    v: 1,
    business: { name: "Shine Bros", phone: "555 0100", reviewUrl: "https://g.page/r/shine" },
    job: { title: "House wash", place: "12 Oak St", date: "12 November 2026" },
    cover: cover ? item(cover) : null,
    sections: rest.length ? [{ label: rest[0].label, pairs: rest.map(item) }] : [],
    badge: false,
    ...over,
  };
}

export function sendBody(jobId: string, pairs: Pair[], over: Record<string, unknown> = {}, extra: Record<string, unknown> = {}) {
  return {
    jobId,
    job: { clientName: "Dana Ruiz", clientPhone: "555 0199", address: "12 Oak St, Springfield", service: "House wash" },
    manifest: manifest(pairs, over),
    ...extra,
  };
}

/** Sends a brand-new gallery. Returns its id and the response body. */
export async function send(token: string, pairs: Pair[], o: { id?: string; jobId?: string; over?: Record<string, unknown>; extra?: Record<string, unknown> } = {}) {
  const id = o.id ?? crypto.randomUUID();
  const res = await call("POST", `/v1/galleries/${id}/publish`, { token, body: sendBody(o.jobId ?? `job-${id}`, pairs, o.over, o.extra) });
  return { id, res, body: await json(res) };
}

export { env };

/** A made-up Google service account key (for the Play purchase and Play Integrity tests). */
export async function fakeServiceAccount(): Promise<string> {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"],
  )) as CryptoKeyPair;
  const der = new Uint8Array((await crypto.subtle.exportKey("pkcs8", pair.privateKey)) as ArrayBuffer);
  const b64 = btoa(String.fromCharCode(...der)).replace(/(.{64})/g, "$1\n");
  return JSON.stringify({ client_email: "play@test.iam.gserviceaccount.com", private_key: `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n` });
}
