import { describe, expect, it } from "vitest";
import { call, env, json, makePairs, send, signIn } from "./helpers";

describe("API keys and the Zapier/Make API", () => {
  it("keys are Pro only", async () => {
    const { token } = await signIn();
    expect((await call("POST", "/v1/api-keys", { token })).status).toBe(402);
  });

  it("a key works, lists galleries, finds one by CRM job id, and can add jobs", async () => {
    const { token } = await signIn("pro");
    const { key } = await json(await call("POST", "/v1/api-keys", { token }));
    expect(key).toMatch(/^ps_/);
    const h = { "x-api-key": key };

    expect((await json(await call("GET", "/api/v1/me", { headers: h }))).plan).toBe("pro");
    const { body } = await send(token, await makePairs(token, 1), { extra: { externalId: "JOB-77" } });

    const list = await json(await call("GET", "/api/v1/galleries", { headers: h }));
    expect(list.galleries[0].url).toBe(body.url);
    expect(list.galleries[0].job.clientName).toBe("Dana Ruiz");

    const found = await json(await call("GET", "/api/v1/galleries/lookup?external_id=JOB-77", { headers: h }));
    expect(found.url).toBe(body.url);
    expect((await call("GET", "/api/v1/galleries/lookup?external_id=nope", { headers: h })).status).toBe(404);

    const created = await call("POST", "/api/v1/jobs", { headers: h, body: { id: "A-1", client_name: "Kim" } });
    expect(created.status).toBe(201);
    const pulled = await json(await call("GET", "/v1/crm/jobs", { token }));
    expect(pulled.jobs[0].externalId).toBe("A-1");
  });

  it("bad or deleted keys are refused", async () => {
    const { token } = await signIn("pro");
    expect((await call("GET", "/api/v1/me", { headers: { "x-api-key": "ps_nope" } })).status).toBe(401);
    const { id, key } = await json(await call("POST", "/v1/api-keys", { token }));
    await call("DELETE", `/v1/api-keys/${id}`, { token });
    expect((await call("GET", "/api/v1/me", { headers: { "x-api-key": key } })).status).toBe(401);
  });

  it("1,000 calls a day", async () => {
    const { token, account } = await signIn("pro");
    const { key } = await json(await call("POST", "/v1/api-keys", { token }));
    await env.DB.prepare(`INSERT INTO counters (account_id, name, period, count) VALUES (?1, 'api', ?2, 1000)`)
      .bind(account.id, new Date().toISOString().slice(0, 10)).run();
    expect((await call("GET", "/api/v1/me", { headers: { "x-api-key": key } })).status).toBe(429);
  });
});
