export interface Env {
  DB: D1Database;
  PHOTOS: R2Bucket;
  DEV_MODE: string;
  GOOGLE_CLIENT_ID: string;
  FREE_STORAGE_BRAKE_BYTES: string;
  /** Tests only: a JSON Web Key Set to verify Google tokens against, instead of Google's. */
  GOOGLE_JWKS_JSON?: string;
}

export type Plan = "free" | "pro";

export interface Account {
  id: string;
  plan: Plan;
  inbound_token: string;
  google_sub: string | null;
  device_id: string | null;
  email: string | null;
}

export type Vars = { account: Account };
export type AppEnv = { Bindings: Env; Variables: Vars };

export const isDev = (env: Env) => env.DEV_MODE === "true";

declare global {
  namespace Cloudflare {
    interface Env {
      DB: D1Database;
      PHOTOS: R2Bucket;
      DEV_MODE: string;
      GOOGLE_CLIENT_ID: string;
      FREE_STORAGE_BRAKE_BYTES: string;
      GOOGLE_JWKS_JSON?: string;
      TEST_MIGRATIONS: import("@cloudflare/vitest-plugin").D1Migration[];
    }
  }
}
