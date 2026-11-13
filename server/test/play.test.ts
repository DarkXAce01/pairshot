import { env } from "cloudflare:test";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { checkVoided, resetPlayCache } from "../src/play";
import { call, fakeServiceAccount, json, signIn } from "./helpers";

let serviceAccount = "";

beforeAll(async () => { serviceAccount = await fakeServiceAccount(); });

afterEach(() => { vi.restoreAllMocks(); resetPlayCache(); });

type Purchase = { purchaseState: number; acknowledgementState: number; orderId?: string };

/** Fakes Google: the token endpoint, one product purchase per token, acknowledge, and voided purchases. */
function fakeGoogle(purchases: Record<string, Purchase | number>, voided: string[] = []) {
  const acks: string[] = [];
  const spy = vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
    const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url);
    if (url.host === "oauth2.googleapis.com") return Response.json({ access_token: "at", expires_in: 3600 });
    expect(new Headers(init?.headers).get("authorization")).toBe("Bearer at");
    const path = decodeURIComponent(url.pathname);
    if (path.endsWith("/purchases/voidedpurchases")) return Response.json({ voidedPurchases: voided.map((purchaseToken) => ({ purchaseToken })) });
    const m = path.match(/\/applications\/com\.greasepencil\.pairshot\/purchases\/products\/pro\/tokens\/([^:]+)(:acknowledge)?$/);
    if (!m) return new Response("no", { status: 404 });
    const p = purchases[m[1]];
    if (p === undefined) return new Response("unknown", { status: 404 });
    if (typeof p === "number") return new Response("err", { status: p });
    if (m[2]) { acks.push(m[1]); p.acknowledgementState = 1; return new Response(null, { status: 204 }); }
    return Response.json({ ...p, orderId: p.orderId ?? "GPA.1", purchaseTimeMillis: "1700000000000" });
  });
  return { spy, acks };
}

const claim = (token: string, purchaseToken: unknown, withKey = true) =>
  call("POST", "/v1/pro/claim", { token, body: { purchaseToken, productId: "pro" }, env: withKey ? { PLAY_SERVICE_ACCOUNT: serviceAccount } : {} });

const plan = async (token: string) => (await json(await call("GET", "/v1/me", { token }))).plan;

describe("buying Pro", () => {
  it("is off until the service account is set", async () => {
    const { token } = await signIn();
    const res = await claim(token, "purchase-token-off", false);
    expect(res.status).toBe(501);
    expect((await json(res)).error).toBe("billing_not_configured");
  });

  it("a paid purchase makes the account Pro and is acknowledged", async () => {
    const { token } = await signIn();
    const g = fakeGoogle({ "tok-paid-0001": { purchaseState: 0, acknowledgementState: 0 } });
    const res = await claim(token, "tok-paid-0001");
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.status).toBe("pro");
    expect(body.account.plan).toBe("pro");
    expect(g.acks).toEqual(["tok-paid-0001"]);
    expect(await plan(token)).toBe("pro");
    // Claiming again (e.g. "Restore purchase") is fine and doesn't acknowledge twice.
    expect((await claim(token, "tok-paid-0001")).status).toBe(200);
    expect(g.acks).toHaveLength(1);
  });

  it("a pending purchase waits", async () => {
    const { token } = await signIn();
    fakeGoogle({ "tok-pending-01": { purchaseState: 2, acknowledgementState: 0 } });
    const res = await claim(token, "tok-pending-01");
    expect(res.status).toBe(202);
    expect((await json(res)).status).toBe("pending");
    expect(await plan(token)).toBe("free");
  });

  it("cancelled or unknown purchases don't unlock Pro", async () => {
    const { token } = await signIn();
    fakeGoogle({ "tok-cancel-01": { purchaseState: 1, acknowledgementState: 0 } });
    expect((await claim(token, "tok-cancel-01")).status).toBe(402);
    expect((await claim(token, "tok-unknown-1")).status).toBe(402);
    expect(await plan(token)).toBe("free");
  });

  it("one purchase unlocks one account", async () => {
    const a = await signIn();
    const b = await signIn();
    fakeGoogle({ "tok-shared-01": { purchaseState: 0, acknowledgementState: 1 } });
    expect((await claim(a.token, "tok-shared-01")).status).toBe(200);
    const res = await claim(b.token, "tok-shared-01");
    expect(res.status).toBe(409);
    expect((await json(res)).error).toBe("used_elsewhere");
    expect(await plan(b.token)).toBe("free");
  });

  it("a Google outage is a retryable 502, and nothing changes", async () => {
    const { token } = await signIn();
    fakeGoogle({ "tok-outage-01": 503 });
    expect((await claim(token, "tok-outage-01")).status).toBe(502);
    expect(await plan(token)).toBe("free");
  });

  it("rejects junk", async () => {
    const { token } = await signIn();
    expect((await claim(token, 42)).status).toBe(400);
    const wrong = await call("POST", "/v1/pro/claim", { token, body: { purchaseToken: "tok-wrong-prod", productId: "gold" }, env: { PLAY_SERVICE_ACCOUNT: serviceAccount } });
    expect(wrong.status).toBe(400);
  });

  it("a refund switches the account back to free", async () => {
    const { token } = await signIn();
    fakeGoogle({ "tok-refund-01": { purchaseState: 0, acknowledgementState: 1 } });
    await claim(token, "tok-refund-01");
    expect(await plan(token)).toBe("pro");
    vi.restoreAllMocks();
    fakeGoogle({}, ["tok-refund-01", "someone-elses-token"]);
    expect(await checkVoided({ ...env, PLAY_SERVICE_ACCOUNT: serviceAccount })).toBe(1);
    expect(await plan(token)).toBe("free");
    // And the refunded purchase can't be claimed again.
    expect((await claim(token, "tok-refund-01")).status).toBe(402);
  });

  it("deleting the account frees the purchase for a new one", async () => {
    const a = await signIn();
    fakeGoogle({ "tok-reuse-001": { purchaseState: 0, acknowledgementState: 1 } });
    await claim(a.token, "tok-reuse-001");
    expect((await call("DELETE", "/v1/me", { token: a.token })).status).toBe(200);
    const b = await signIn();
    expect((await claim(b.token, "tok-reuse-001")).status).toBe(200);
  });
});
