import { SignJWT, exportJWK, generateKeyPair } from "jose";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { resetServiceTokens } from "../src/google";
import { problems, requestHashFor, type Verdict } from "../src/integrity";
import { call, fakeServiceAccount, json } from "./helpers";

const good = (hash: string, at = Date.now()): Verdict => ({
  requestDetails: { requestPackageName: "com.greasepencil.pairshot", requestHash: hash, timestampMillis: String(at) },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.greasepencil.pairshot" },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
});

describe("integrity verdicts", () => {
  const expect_ = { packageName: "com.greasepencil.pairshot", requestHash: "h1", at: 1_000_000_000 };

  it("a Play-installed app on a real phone passes", () => {
    expect(problems(good("h1", expect_.at), expect_)).toEqual([]);
  });

  it("names each problem", () => {
    const v = good("other", expect_.at - 3_600_000);
    v.appIntegrity!.appRecognitionVerdict = "UNRECOGNIZED_VERSION";
    v.deviceIntegrity!.deviceRecognitionVerdict = [];
    v.requestDetails!.requestPackageName = "com.evil";
    expect(problems(v, expect_).sort()).toEqual(["app_not_recognized", "device_not_trusted", "stale", "wrong_package", "wrong_request"]);
  });
});

describe("sign-in with integrity", async () => {
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(publicKey)), kid: "k1", alg: "RS256", use: "sig" };
  let sa = "";
  beforeAll(async () => { sa = await fakeServiceAccount(); });
  afterEach(() => { vi.restoreAllMocks(); resetServiceTokens(); });

  const env = (mode: string) => ({
    DEV_MODE: "false", GOOGLE_CLIENT_ID: "client-123", GOOGLE_JWKS_JSON: JSON.stringify({ keys: [jwk] }),
    PLAY_SERVICE_ACCOUNT: sa, INTEGRITY_MODE: mode,
  });
  const idToken = (sub: string) =>
    new SignJWT({ email: `${sub}@example.com` }).setProtectedHeader({ alg: "RS256", kid: "k1" })
      .setIssuer("https://accounts.google.com").setAudience("client-123").setSubject(sub).setIssuedAt().setExpirationTime("10m").sign(privateKey);

  /** Google decodes "good-<deviceId>" into a passing verdict for that phone, anything else into a failing one. */
  function fakeIntegrity(down = false) {
    return vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      if (url.startsWith("https://oauth2.googleapis.com")) return Response.json({ access_token: "at", expires_in: 3600 });
      if (url.startsWith("https://playintegrity.googleapis.com/v1/com.greasepencil.pairshot:decodeIntegrityToken")) {
        if (down) return new Response("unavailable", { status: 503 });
        const token = JSON.parse(String(init!.body)).integrity_token as string;
        const device = token.replace(/^(good|bad)-/, "");
        const v = good(await requestHashFor(device));
        if (token.startsWith("bad-")) v.appIntegrity!.appRecognitionVerdict = "UNRECOGNIZED_VERSION";
        return Response.json({ tokenPayloadExternal: v });
      }
      return new Response("unexpected", { status: 500 });
    });
  }

  const auth = async (mode: string, deviceId: string, integrityToken?: string, sub = deviceId) =>
    call("POST", "/v1/auth", { env: env(mode), body: { deviceId, googleIdToken: await idToken(sub), integrityToken } });

  it("enforce: a good token signs in", async () => {
    fakeIntegrity();
    expect((await auth("enforce", "phone-int-001", "good-phone-int-001")).status).toBe(200);
  });

  it("enforce: a modified app, a token for another phone, or no token is refused", async () => {
    fakeIntegrity();
    for (const [device, token] of [["phone-int-002", "bad-phone-int-002"], ["phone-int-003", "good-phone-int-999"], ["phone-int-004", undefined]] as const) {
      const res = await auth("enforce", device, token);
      expect(res.status).toBe(403);
      expect((await json(res)).error).toBe("integrity_failed");
    }
  });

  it("log: never refuses", async () => {
    fakeIntegrity();
    expect((await auth("log", "phone-int-005", "bad-phone-int-005")).status).toBe(200);
  });

  it("off: Google isn't asked at all", async () => {
    const spy = fakeIntegrity();
    expect((await auth("off", "phone-int-006")).status).toBe(200);
    expect(spy).not.toHaveBeenCalled();
  });

  it("Google being down doesn't lock people out", async () => {
    fakeIntegrity(true);
    expect((await auth("enforce", "phone-int-007", "good-phone-int-007")).status).toBe(200);
  });
});

describe("request hash", () => {
  it("matches the app's (PlayIntegrityTest)", async () => {
    expect(await requestHashFor("phone-1")).toBe("8aa2a70a2ad2225a0ebd1e7e3f7b0ed401385831d204a311d69f2d29ad552553");
  });
});
