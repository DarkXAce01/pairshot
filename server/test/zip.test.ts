import { describe, expect, it } from "vitest";
import { crc32, photoName, zipStream } from "../src/zip";
import { call, env, makePairs, send, signIn } from "./helpers";

const UA = { "user-agent": "Mozilla/5.0 (Linux; Android 15) Chrome/130" };

/** Reads a stored ZIP's central directory: names, sizes, CRCs, and checks each file's bytes against it. */
function readZip(buf: Uint8Array) {
  const v = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const end = buf.length - 22;
  expect(v.getUint32(end, true)).toBe(0x06054b50);
  const count = v.getUint16(end + 10, true);
  let p = v.getUint32(end + 16, true);
  const files: { name: string; size: number; crc: number }[] = [];
  for (let i = 0; i < count; i++) {
    expect(v.getUint32(p, true)).toBe(0x02014b50);
    const crc = v.getUint32(p + 16, true);
    const size = v.getUint32(p + 24, true);
    const nameLen = v.getUint16(p + 28, true);
    const offset = v.getUint32(p + 42, true);
    const name = new TextDecoder().decode(buf.subarray(p + 46, p + 46 + nameLen));
    const dataStart = offset + 30 + v.getUint16(offset + 26, true);
    expect(crc32(buf.subarray(dataStart, dataStart + size))).toBe(crc);
    files.push({ name, size, crc });
    p += 46 + nameLen;
  }
  return files;
}

describe("zip", () => {
  it("crc32 matches the standard check value", () => {
    expect(crc32(new TextEncoder().encode("123456789"))).toBe(0xcbf43926);
  });

  it("writes a valid stored archive", async () => {
    const a = new TextEncoder().encode("hello");
    const b = new Uint8Array(70_000).map((_, i) => i % 251);
    const zip = new Uint8Array(await new Response(zipStream([
      { name: "a.txt", open: async () => new Response(a).body },
      { name: "gone.jpg", open: async () => null },
      { name: "b.bin", open: async () => new Response(b).body },
    ])).arrayBuffer());
    const files = readZip(zip);
    expect(files.map((f) => f.name)).toEqual(["a.txt", "b.bin"]);
    expect(files[1].size).toBe(70_000);
  });

  it("names photos safely", () => {
    expect(photoName(3, "Front steps", "after")).toBe("03 Front steps - after.jpg");
    expect(photoName(12, 'a/b:c*"d', "before")).toBe("12 a b c d - before.jpg");
    expect(photoName(1, undefined, "before")).toBe("01 - before.jpg");
  });
});

describe("download all photos", () => {
  it("the page links to it and it holds every photo", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 3, "Driveway");
    const { body } = await send(token, pairs);
    const path = new URL(body.url).pathname;
    expect(await (await call("GET", path, { headers: UA })).text()).toContain(`${path}/photos.zip`);

    const res = await call("GET", `${path}/photos.zip`);
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("application/zip");
    expect(res.headers.get("content-disposition")).toContain('filename="House-wash.zip"');
    const files = readZip(new Uint8Array(await res.arrayBuffer()));
    expect(files).toHaveLength(6);
    expect(files.every((f) => f.name.includes("Driveway"))).toBe(true);
  });

  it("is gone once the gallery is closed", async () => {
    const { token } = await signIn();
    const { body, id } = await send(token, await makePairs(token, 1));
    await call("DELETE", `/v1/galleries/${id}`, { token });
    expect((await call("GET", `${new URL(body.url).pathname}/photos.zip`)).status).toBe(404);
  });

  it("is limited per gallery per day", async () => {
    const { token } = await signIn();
    const { body, id } = await send(token, await makePairs(token, 1));
    const g = await env.DB.prepare(`SELECT account_id FROM galleries WHERE id = ?1`).bind(id).first<{ account_id: string }>();
    await env.DB.prepare(`INSERT INTO counters (account_id, name, period, count) VALUES (?1, ?2, ?3, 20)`)
      .bind(g!.account_id, `zip:${id}`, new Date().toISOString().slice(0, 10)).run();
    expect((await call("GET", `${new URL(body.url).pathname}/photos.zip`)).status).toBe(429);
  });
});
