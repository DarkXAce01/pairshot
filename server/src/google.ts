import { importPKCS8, SignJWT } from "jose";
import type { Env } from "./env";
import { now } from "./util";

/** The service account key (secret PLAY_SERVICE_ACCOUNT) used for Play purchases and Play Integrity. */
interface ServiceAccount { client_email: string; private_key: string; token_uri?: string }

const cache = new Map<string, { key: string; token: string; until: number }>();

/** An OAuth access token for one Google API [scope], made from the service account key. */
export async function serviceToken(env: Env, scope: string): Promise<string> {
  const raw = env.PLAY_SERVICE_ACCOUNT!;
  const hit = cache.get(scope);
  if (hit && hit.key === raw && hit.until > now() + 60_000) return hit.token;
  let sa: ServiceAccount;
  try {
    sa = JSON.parse(raw);
  } catch {
    throw new Error("PLAY_SERVICE_ACCOUNT isn't valid JSON");
  }
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const at = Math.floor(now() / 1000);
  const assertion = await new SignJWT({ scope })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(at)
    .setExpirationTime(at + 3600)
    .sign(await importPKCS8(sa.private_key, "RS256"));
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!res.ok) throw new Error(`Google token: ${res.status} ${await res.text()}`);
  const j = (await res.json()) as { access_token: string; expires_in: number };
  cache.set(scope, { key: raw, token: j.access_token, until: now() + j.expires_in * 1000 });
  return j.access_token;
}

/** Tests: forget cached access tokens. */
export const resetServiceTokens = () => cache.clear();
