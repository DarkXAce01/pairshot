import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import * as account from "./account";
import * as api from "./api";
import { requireAccount, signIn } from "./auth";
import * as crm from "./crm";
import type { AppEnv, Env } from "./env";
import * as galleries from "./galleries";
import { LIMITS } from "./limits";
import { deleteUnreferenced, uploadPhoto } from "./photos";
import * as pub from "./public";
import { now } from "./util";
import * as hooks from "./webhooks";

export const app = new Hono<AppEnv>();

app.onError((err, c) => {
  if (err instanceof HTTPException) return err.getResponse();
  console.error(err);
  return c.json({ error: "server_error", message: "Something went wrong on our side. It'll retry." }, 500);
});
app.notFound((c) => c.json({ error: "not_found", message: "Not here." }, 404));

// Public pages
app.get("/", pub.home);
app.get("/privacy", pub.privacy);
app.get("/delete-account", pub.deleteInfo);
app.get("/g/:slug", pub.galleryPage);
app.get("/i/:account/:hash", pub.photo);

// CRM → Pairshot (secret address, no sign-in)
app.post("/hooks/in/:token", crm.inbound);

// The phone
app.post("/v1/auth", signIn);
const v1 = new Hono<AppEnv>();
v1.use("*", requireAccount);
v1.get("/me", account.me);
v1.delete("/me", account.deleteAll);
v1.post("/dev/plan", account.devPlan);
v1.get("/usage", galleries.usage);
v1.put("/photos/:hash", uploadPhoto);
v1.get("/galleries", galleries.list);
v1.post("/galleries/:id/prepare", galleries.prepare);
v1.post("/galleries/:id/publish", galleries.publish);
v1.delete("/galleries/:id", galleries.revoke);
v1.get("/crm/jobs", crm.pull);
v1.post("/crm/rotate", crm.rotate);
v1.get("/webhooks", hooks.listHooks);
v1.post("/webhooks", hooks.addHook);
v1.delete("/webhooks/:id", hooks.deleteHook);
v1.post("/webhooks/:id/test", hooks.testHook);
v1.get("/api-keys", api.listKeys);
v1.post("/api-keys", api.createKey);
v1.delete("/api-keys/:id", api.deleteKey);
app.route("/v1", v1);

// Zapier / Make / scripts
const pubApi = new Hono<AppEnv>();
pubApi.use("*", api.requireKey);
pubApi.get("/me", api.me);
pubApi.get("/galleries", api.apiGalleries);
pubApi.get("/galleries/lookup", api.apiLookup);
pubApi.post("/jobs", api.apiCreateJob);
app.route("/api/v1", pubApi);

/** Every 5 minutes: expire galleries, delete photos nothing shows, retry webhooks, tidy counters. */
export async function housekeeping(env: Env) {
  await galleries.expireGalleries(env);
  await deleteUnreferenced(env, LIMITS.both.unreferencedGraceMs);
  await hooks.retryDue(env);
  const old = new Date(now() - 40 * 86_400_000).toISOString().slice(0, 10);
  await env.DB.prepare(`DELETE FROM counters WHERE period < ?1 AND length(period) >= 10`).bind(old).run();
  await env.DB.prepare(`DELETE FROM webhook_deliveries WHERE created_at < ?1 AND status != 'pending'`).bind(now() - 30 * 86_400_000).run();
}

export default {
  fetch: app.fetch,
  scheduled: (_event, env, ctx) => ctx.waitUntil(housekeeping(env)),
} satisfies ExportedHandler<Env>;
