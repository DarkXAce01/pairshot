import { SignJWT, exportJWK, generateKeyPair } from "jose";
import { describe, expect, it } from "vitest";
import { call, json, signIn } from "./helpers";

describe("signing in", () => {
  it("dev mode: a phone gets an account, and the same phone gets the same one back", async () => {
    const first = await call("POST", "/v1/auth", { body: { deviceId: "phone-aaaa-1111" } });
    const second = await call("POST", "/v1/auth", { body: { deviceId: "phone-aaaa-1111" } });
    const a = await json(first), b = await json(second);
    expect(first.status).toBe(200);
    expect(a.account.id).toBe(b.account.id);
    expect(a.token).not.toBe(b.token);
    expect(a.account.plan).toBe("free");
    expect(a.account.inboundUrl).toMatch(/^https:\/\/pairshot\.test\/hooks\/in\/.{20,}/);
  });

  it("needs a token for the phone API, and rejects a made-up one", async () => {
    expect((await call("GET", "/v1/me")).status).toBe(401);
    expect((await call("GET", "/v1/me", { token: "nope" })).status).toBe(401);
    const { token } = await signIn();
    expect((await call("GET", "/v1/me", { token })).status).toBe(200);
  });

  it("outside dev mode, Google sign-in is required", async () => {
    const res = await call("POST", "/v1/auth", { body: { deviceId: "phone-bbbb-2222" }, env: { DEV_MODE: "false" } });
    expect(res.status).toBe(401);
    expect((await json(res)).error).toBe("sign_in_needed");
  });

  describe("with Google", async () => {
    const { publicKey, privateKey } = await generateKeyPair("RS256");
    const jwk = { ...(await exportJWK(publicKey)), kid: "k1", alg: "RS256", use: "sig" };
    const googleEnv = { DEV_MODE: "false", GOOGLE_CLIENT_ID: "client-123", GOOGLE_JWKS_JSON: JSON.stringify({ keys: [jwk] }) };
    const idToken = (sub: string, aud = "client-123") =>
      new SignJWT({ email: `${sub}@example.com` }).setProtectedHeader({ alg: "RS256", kid: "k1" })
        .setIssuer("https://accounts.google.com").setAudience(aud).setSubject(sub).setIssuedAt().setExpirationTime("10m").sign(privateKey);

    it("one account per Google account", async () => {
      const a = await json(await call("POST", "/v1/auth", { env: googleEnv, body: { deviceId: "phone-g-0001", googleIdToken: await idToken("u1") } }));
      const b = await json(await call("POST", "/v1/auth", { env: googleEnv, body: { deviceId: "phone-g-0001", googleIdToken: await idToken("u1") } }));
      expect(a.account.id).toBe(b.account.id);
      expect(a.account.email).toBe("u1@example.com");
    });

    it("a second Google account on the same phone is refused (one free account per phone)", async () => {
      await call("POST", "/v1/auth", { env: googleEnv, body: { deviceId: "phone-g-0002", googleIdToken: await idToken("u2") } });
      const res = await call("POST", "/v1/auth", { env: googleEnv, body: { deviceId: "phone-g-0002", googleIdToken: await idToken("u3") } });
      expect(res.status).toBe(409);
      expect((await json(res)).message).toContain("u2@example.com");
    });

    it("a token for another app is refused", async () => {
      const res = await call("POST", "/v1/auth", { env: googleEnv, body: { deviceId: "phone-g-0003", googleIdToken: await idToken("u4", "someone-else") } });
      expect(res.status).toBe(401);
    });
  });

  it("limits requests per minute", async () => {
    const { token } = await signIn();
    let last = 200;
    for (let i = 0; i < 62; i++) last = (await call("GET", "/v1/me", { token })).status;
    expect(last).toBe(429);
  });
});
