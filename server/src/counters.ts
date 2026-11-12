import type { Env } from "./env";

/** Adds one to a counter and returns the new value. */
export async function bump(env: Env, accountId: string, name: string, period: string, by = 1): Promise<number> {
  const row = await env.DB.prepare(
    `INSERT INTO counters (account_id, name, period, count) VALUES (?1, ?2, ?3, ?4)
     ON CONFLICT (account_id, name, period) DO UPDATE SET count = count + ?4
     RETURNING count`,
  ).bind(accountId, name, period, by).first<{ count: number }>();
  return row?.count ?? by;
}

export async function read(env: Env, accountId: string, name: string, period: string): Promise<number> {
  const row = await env.DB.prepare(`SELECT count FROM counters WHERE account_id = ?1 AND name = ?2 AND period = ?3`)
    .bind(accountId, name, period).first<{ count: number }>();
  return row?.count ?? 0;
}
