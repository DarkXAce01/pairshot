import { describe, expect, it } from "vitest";
import { call, env, jpeg, json, sha256, signIn, upload } from "./helpers";

const put = (token: string, hash: string, raw: Uint8Array) => call("PUT", `/v1/photos/${hash}`, { token, raw, headers: { "content-type": "image/jpeg" } });

describe("photo uploads", () => {
  it("stores a JPEG under its hash, once", async () => {
    const { token, account } = await signIn();
    const b = jpeg(200);
    const hash = await sha256(b);
    const first = await put(token, hash, b);
    expect(first.status).toBe(201);
    expect((await json(await put(token, hash, b))).stored).toBe(false);
    expect(await env.PHOTOS.head(`p/${account.id}/${hash}`)).not.toBeNull();
  });

  it("accepts WebP too", async () => {
    const { token } = await signIn();
    const b = new Uint8Array(40);
    b.set([...new TextEncoder().encode("RIFF")], 0);
    b.set([...new TextEncoder().encode("WEBP")], 8);
    expect((await put(token, await sha256(b), b)).status).toBe(201);
  });

  it("refuses a photo whose bytes don't match its name", async () => {
    const { token } = await signIn();
    expect((await put(token, await sha256(jpeg()), jpeg())).status).toBe(422);
  });

  it("refuses things that aren't photos", async () => {
    const { token } = await signIn();
    const html = new TextEncoder().encode("<html><script>alert(1)</script></html>");
    expect((await put(token, await sha256(html), html)).status).toBe(415);
  });

  it("refuses photos over 1 MB", async () => {
    const { token } = await signIn();
    const big = jpeg(1024 * 1024 + 10);
    expect((await put(token, await sha256(big), big)).status).toBe(413);
  });

  it("free accounts stop at 50 MB", async () => {
    const { token, account } = await signIn();
    await env.DB.prepare(`INSERT INTO photos (account_id, hash, bytes, content_type, created_at) VALUES (?1, ?2, ?3, 'image/jpeg', 0)`)
      .bind(account.id, "f".repeat(64), 50 * 1024 * 1024 - 10).run();
    const b = jpeg(100);
    const full = await put(token, await sha256(b), b);
    expect(full.status).toBe(507);
    expect((await json(full)).error).toBe("storage_full");
  });

  it("the global brake pauses free uploads but never Pro", async () => {
    const free = await signIn("free");
    const pro = await signIn("pro");
    const total = (await env.DB.prepare(`SELECT COALESCE(SUM(bytes),0) AS n FROM photos`).first<{ n: number }>())!.n;
    const brake = { FREE_STORAGE_BRAKE_BYTES: String(total + 50) };
    const b1 = jpeg(100), b2 = jpeg(100);
    const r1 = await call("PUT", `/v1/photos/${await sha256(b1)}`, { token: free.token, raw: b1, env: brake });
    const r2 = await call("PUT", `/v1/photos/${await sha256(b2)}`, { token: pro.token, raw: b2, env: brake });
    expect(r1.status).toBe(507);
    expect((await json(r1)).error).toBe("busy");
    expect(r2.status).toBe(201);
  });

  it("an upload helper sanity check", async () => {
    const { token } = await signIn();
    expect(await upload(token)).toMatch(/^[0-9a-f]{64}$/);
  });
});
