import type { Context } from "hono";
import { publicAccount } from "./auth";
import type { AppEnv } from "./env";
import { isDev } from "./env";
import { fail } from "./util";

export function me(c: Context<AppEnv>) {
  return c.json(publicAccount(c, c.get("account")));
}

/** DELETE /v1/me: every gallery, photo, webhook and key, gone. */
export async function deleteAll(c: Context<AppEnv>) {
  const a = c.get("account");
  const env = c.env;
  let cursor: string | undefined;
  do {
    const page = await env.PHOTOS.list({ prefix: `p/${a.id}/`, cursor });
    if (page.objects.length) await env.PHOTOS.delete(page.objects.map((o) => o.key));
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
  await env.DB.batch([
    env.DB.prepare(`DELETE FROM counters WHERE account_id = ?1 OR account_id IN (SELECT id FROM galleries WHERE account_id = ?1)`).bind(a.id),
    env.DB.prepare(`DELETE FROM webhook_deliveries WHERE account_id = ?1`).bind(a.id),
    env.DB.prepare(`DELETE FROM accounts WHERE id = ?1`).bind(a.id), // cascades to everything else
  ]);
  return c.json({ ok: true });
}

/** POST /v1/dev/plan { plan }: switch Free/Pro while testing. Only in dev mode. */
export async function devPlan(c: Context<AppEnv>) {
  if (!isDev(c.env)) fail(404, "not_found", "Not here.");
  const { plan } = await c.req.json<{ plan?: string }>().catch(() => ({ plan: undefined }));
  if (plan !== "free" && plan !== "pro") fail(400, "bad_plan", "plan must be free or pro");
  await c.env.DB.prepare(`UPDATE accounts SET plan = ?2 WHERE id = ?1`).bind(c.get("account").id, plan).run();
  return c.json({ plan });
}
