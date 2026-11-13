import { defineConfig } from "vitest/config";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";

export default defineConfig(async () => {
  const migrations = await readD1Migrations("./migrations");
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: { bindings: { TEST_MIGRATIONS: migrations, DEV_MODE: "true", GOOGLE_CLIENT_ID: "", FREE_STORAGE_BRAKE_BYTES: "10737418240", PLAY_PACKAGE: "com.greasepencil.pairshot", PRO_PRODUCT_ID: "pro" } },
      }),
    ],
    test: { setupFiles: ["./test/setup.ts"] },
  };
});
