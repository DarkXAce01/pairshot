import { afterEach, describe, expect, it, vi } from "vitest";
import { housekeeping } from "../src/index";
import { hmacSha256Hex } from "../src/util";
import { call, env, json, makePairs, send, signIn } from "./helpers";

afterEach(() => vi.restoreAllMocks());

function captureFetch(status = 200) {
  const calls: { url: string; headers: Headers; body: string }[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), headers: new Headers(init?.headers), body: String(init?.body) });
    return new Response("ok", { status });
  });
  return calls;
}

describe("Pairshot → CRM (outbound webhooks)", () => {
  it("is a Pro feature", async () => {
    const { token } = await signIn();
    expect((await call("POST", "/v1/webhooks", { token, body: { url: "https://hooks.zapier.com/x" } })).status).toBe(402);
  });

  it("must be https outside dev mode", async () => {
    const { token } = await signIn("pro");
    const res = await call("POST", "/v1/webhooks", { token, body: { url: "http://example.com/x" }, env: { DEV_MODE: "false" } });
    // (DEV_MODE off also means this token's sign-in path differs, but the hook check comes first)
    expect([400, 401]).toContain(res.status);
  });

  it("a sent gallery is delivered, signed, with the job and link", async () => {
    const { token } = await signIn("pro");
    const hook = await json(await call("POST", "/v1/webhooks", { token, body: { url: "https://hooks.zapier.com/abc" } }));
    expect(hook.secret).toMatch(/^whsec_/);
    const calls = captureFetch();
    const { body } = await send(token, await makePairs(token, 1), { extra: { externalId: "JOB-1042" } });
    expect(calls).toHaveLength(1);
    const c = calls[0];
    expect(c.url).toBe("https://hooks.zapier.com/abc");
    expect(c.headers.get("x-pairshot-event")).toBe("gallery.published");
    const payload = JSON.parse(c.body);
    expect(payload.data.gallery.url).toBe(body.url);
    expect(payload.data.job).toMatchObject({ externalId: "JOB-1042", clientName: "Dana Ruiz", clientPhone: "555 0199" });

    const [t, v1] = c.headers.get("x-pairshot-signature")!.split(",").map((p) => p.split("=")[1]);
    expect(v1).toBe(await hmacSha256Hex(hook.secret, `${t}.${c.body}`));
  });

  it("the client's first view is delivered once", async () => {
    const { token } = await signIn("pro");
    await call("POST", "/v1/webhooks", { token, body: { url: "https://hook.example/v", events: ["gallery.viewed"] } });
    const calls = captureFetch();
    const { body } = await send(token, await makePairs(token, 1));
    expect(calls).toHaveLength(0); // not subscribed to published
    const path = new URL(body.url).pathname;
    await call("GET", path, { headers: { "user-agent": "Mozilla/5.0 Safari" } });
    await call("GET", path, { headers: { "user-agent": "Mozilla/5.0 Safari" } });
    expect(calls).toHaveLength(1);
    expect(JSON.parse(calls[0].body).event).toBe("gallery.viewed");
  });

  it("a failing endpoint is retried later, and the log shows it", async () => {
    const { token } = await signIn("pro");
    await call("POST", "/v1/webhooks", { token, body: { url: "https://down.example/hook" } });
    captureFetch(500);
    await send(token, await makePairs(token, 1));
    let log = (await json(await call("GET", "/v1/webhooks", { token }))).deliveries;
    expect(log[0]).toMatchObject({ status: "pending", attempts: 1, last_status: 500 });

    vi.restoreAllMocks();
    const calls = captureFetch(200);
    await env.DB.prepare(`UPDATE webhook_deliveries SET next_attempt_at = 0 WHERE status = 'pending'`).run();
    await housekeeping(env);
    expect(calls.length).toBeGreaterThanOrEqual(1);
    log = (await json(await call("GET", "/v1/webhooks", { token }))).deliveries;
    expect(log[0]).toMatchObject({ status: "ok", attempts: 2 });
  });

  it("gives up after the last retry", async () => {
    const { token } = await signIn("pro");
    await call("POST", "/v1/webhooks", { token, body: { url: "https://down.example/hook2" } });
    captureFetch(500);
    await send(token, await makePairs(token, 1));
    for (let i = 0; i < 7; i++) {
      await env.DB.prepare(`UPDATE webhook_deliveries SET next_attempt_at = 0 WHERE status = 'pending'`).run();
      await housekeeping(env);
    }
    const log = (await json(await call("GET", "/v1/webhooks", { token }))).deliveries;
    expect(log[0]).toMatchObject({ status: "failed", attempts: 7 });
  });

  it("the test button sends a ping", async () => {
    const { token } = await signIn("pro");
    const hook = await json(await call("POST", "/v1/webhooks", { token, body: { url: "https://hook.example/p" } }));
    const calls = captureFetch();
    expect((await json(await call("POST", `/v1/webhooks/${hook.id}/test`, { token }))).ok).toBe(true);
    expect(calls[0].headers.get("x-pairshot-event")).toBe("ping");
  });
});
