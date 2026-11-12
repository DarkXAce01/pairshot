import { describe, expect, it } from "vitest";
import { housekeeping } from "../src/index";
import { call, env, makePairs, send, signIn, upload } from "./helpers";

describe("housekeeping (every 5 minutes)", () => {
  it("deletes uploaded photos no gallery uses, after a day", async () => {
    const { token, account } = await signIn();
    const fresh = await upload(token);
    const old = await upload(token);
    await env.DB.prepare(`UPDATE photos SET created_at = 0 WHERE hash = ?1`).bind(old).run();
    await housekeeping(env);
    expect(await env.PHOTOS.head(`p/${account.id}/${old}`)).toBeNull();
    expect(await env.PHOTOS.head(`p/${account.id}/${fresh}`)).not.toBeNull();
  });

  it("expired galleries lose their photos", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 1);
    const { id } = await send(token, pairs);
    await env.DB.prepare(`UPDATE galleries SET expires_at = 1 WHERE id = ?1`).bind(id).run();
    await env.DB.prepare(`UPDATE photos SET created_at = 0 WHERE account_id = ?1`).bind(account.id).run();
    await housekeeping(env);
    expect(await env.PHOTOS.head(`p/${account.id}/${pairs[0].after}`)).toBeNull();
  });

  it("photos in live galleries are kept", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 1);
    await send(token, pairs);
    await env.DB.prepare(`UPDATE photos SET created_at = 0 WHERE account_id = ?1`).bind(account.id).run();
    await housekeeping(env);
    expect(await env.PHOTOS.head(`p/${account.id}/${pairs[0].after}`)).not.toBeNull();
  });

  it("public pages exist", async () => {
    for (const p of ["/", "/privacy", "/delete-account"]) expect((await call("GET", p)).status).toBe(200);
  });
});
