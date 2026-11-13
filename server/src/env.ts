export interface Env {
  DB: D1Database;
  PHOTOS: R2Bucket;
  DEV_MODE: string;
  GOOGLE_CLIENT_ID: string;
  FREE_STORAGE_BRAKE_BYTES: string;
  /** The app's package on Google Play, for checking purchases. */
  PLAY_PACKAGE: string;
  /** Play product id of the one-time Pro purchase. */
  PRO_PRODUCT_ID: string;
  /** Secret: a Google service account's JSON key with access to Play Console. Unset = buying Pro is off. */
  PLAY_SERVICE_ACCOUNT?: string;
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
      PLAY_PACKAGE: string;
      PRO_PRODUCT_ID: string;
      PLAY_SERVICE_ACCOUNT?: string;
      GOOGLE_JWKS_JSON?: string;
      TEST_MIGRATIONS: import("@cloudflare/vitest-plugin").D1Migration[];
    }
  }
}
