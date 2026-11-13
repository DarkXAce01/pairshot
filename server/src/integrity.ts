import type { Env } from "./env";
import { serviceToken } from "./google";
import { fail, now, sha256Hex } from "./util";

/**
 * Play Integrity: proof that a sign-in comes from Pairshot as installed from Google Play, on a real phone.
 * It stops scripts and modified apps from making free accounts in bulk.
 *
 * INTEGRITY_MODE: "off" (default; nothing is checked), "log" (checked and logged, never refused: use this
 * first to see what real users get), "enforce" (refused unless the verdict is good).
 */

export interface Verdict {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
}

/** Problems with a decoded verdict, as short codes; empty means it's good. */
export function problems(v: Verdict, expect: { packageName: string; requestHash: string; at: number }): string[] {
  const out: string[] = [];
  const rd = v.requestDetails ?? {};
  if (rd.requestPackageName !== expect.packageName) out.push("wrong_package");
  if (rd.requestHash !== expect.requestHash) out.push("wrong_request");
  const ts = Number(rd.timestampMillis ?? 0);
  if (!ts || Math.abs(expect.at - ts) > 10 * 60_000) out.push("stale");
  if (v.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") out.push("app_not_recognized");
  if (!(v.deviceIntegrity?.deviceRecognitionVerdict ?? []).includes("MEETS_DEVICE_INTEGRITY")) out.push("device_not_trusted");
  return out;
}

async function decode(env: Env, token: string): Promise<Verdict> {
  const access = await serviceToken(env, "https://www.googleapis.com/auth/playintegrity");
  const res = await fetch(`https://playintegrity.googleapis.com/v1/${encodeURIComponent(env.PLAY_PACKAGE)}:decodeIntegrityToken`, {
    method: "POST",
    headers: { authorization: `Bearer ${access}`, "content-type": "application/json" },
    body: JSON.stringify({ integrity_token: token }),
  });
  if (!res.ok) throw new Error(`integrity decode ${res.status}: ${await res.text()}`);
  return ((await res.json()) as { tokenPayloadExternal: Verdict }).tokenPayloadExternal;
}

/** What the phone must put in its integrity request: ties the token to this sign-in. */
export const requestHashFor = (deviceId: string) => sha256Hex(`pairshot-signin:${deviceId}`);

/** Checks a sign-in's integrity token. Refuses only in "enforce" mode. */
export async function checkSignIn(env: Env, deviceId: string, token: unknown): Promise<void> {
  const mode = env.INTEGRITY_MODE ?? "off";
  if (mode !== "log" && mode !== "enforce") return;
  if (!env.PLAY_SERVICE_ACCOUNT) return;
  let found: string[];
  if (typeof token !== "string" || !token) {
    found = ["no_token"];
  } else {
    try {
      found = problems(await decode(env, token), { packageName: env.PLAY_PACKAGE, requestHash: await requestHashFor(deviceId), at: now() });
    } catch (e) {
      // Google being down must never lock everyone out.
      console.error("integrity", e);
      return;
    }
  }
  if (!found.length) return;
  console.log("integrity", mode, found.join(","));
  if (mode === "enforce") {
    fail(403, "integrity_failed", "This copy of Pairshot can't send links. Install Pairshot from Google Play and try again.");
  }
}
