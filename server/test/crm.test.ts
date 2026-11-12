import { describe, expect, it } from "vitest";
import { normalise, parseTime } from "../src/crm";
import { call, env, json, signIn } from "./helpers";

const hookPath = (inboundUrl: string) => new URL(inboundUrl).pathname;

describe("reading whatever the CRM sends", () => {
  it("plain fields", () => {
    const j = normalise({ external_id: "JOB-1", client_name: "Dana Ruiz", phone: "555", address: "12 Oak St", service: "House wash", scheduled_at: "2026-11-13T09:00:00Z" });
    expect(j).toMatchObject({ externalId: "JOB-1", clientName: "Dana Ruiz", clientPhone: "555", address: "12 Oak St", service: "House wash", cancelled: false });
    expect(j.scheduledAt).toBe(Date.parse("2026-11-13T09:00:00Z"));
  });

  it("Jobber-style nested shapes and first/last names", () => {
    const j = normalise({
      id: 9876, title: "Driveway + patio",
      client: { first_name: "Sam", last_name: "Lee", email: "sam@x.com", phone: "555 1" },
      property: { address1: "4 Bay Rd", city: "Tampa" },
      start_at: 1767258000,
    });
    expect(j.externalId).toBe("9876");
    expect(j.clientName).toBe("Sam Lee");
    expect(j.clientEmail).toBe("sam@x.com");
    expect(j.address).toBe("4 Bay Rd, Tampa");
    expect(j.service).toBe("Driveway + patio");
    expect(j.scheduledAt).toBe(1767258000 * 1000);
  });

  it("camelCase, Zapier-style keys", () => {
    const j = normalise({ jobId: "Z-1", customerName: "Ann", "Service Address": "1 Main", jobType: "Roof" });
    expect(j).toMatchObject({ externalId: "Z-1", clientName: "Ann", address: "1 Main", service: "Roof" });
  });

  it("cancelled jobs", () => {
    expect(normalise({ id: "1", status: "Cancelled" }).cancelled).toBe(true);
    expect(normalise({ id: "1", canceled: "true" }).cancelled).toBe(true);
    expect(normalise({ id: "1", status: "scheduled" }).cancelled).toBe(false);
  });

  it("times in seconds, millis or text", () => {
    expect(parseTime(1767258000)).toBe(1767258000000);
    expect(parseTime(1767258000000)).toBe(1767258000000);
    expect(parseTime("1767258000")).toBe(1767258000000);
    expect(parseTime("not a date")).toBeNull();
  });
});

describe("CRM → phone", () => {
  it("free accounts are told it's a Pro feature", async () => {
    const { account } = await signIn();
    const res = await call("POST", hookPath(account.inboundUrl), { body: { id: "1", client_name: "A" } });
    expect(res.status).toBe(402);
  });

  it("a job sent in shows up for the phone, and an update doesn't duplicate it", async () => {
    const { token, account } = await signIn("pro");
    const hook = hookPath(account.inboundUrl);
    expect((await call("POST", hook, { body: { external_id: "J1", client_name: "Dana", service: "House wash" } })).status).toBe(200);
    expect((await call("POST", hook, { body: { external_id: "J2", client_name: "Sam" } })).status).toBe(200);

    let pulled = await json(await call("GET", "/v1/crm/jobs?since=0", { token }));
    expect(pulled.jobs.map((j: any) => j.externalId)).toEqual(["J1", "J2"]);
    const cursor = pulled.cursor;

    await call("POST", hook, { body: { external_id: "J1", client_name: "Dana Ruiz", service: "House wash" } });
    pulled = await json(await call("GET", `/v1/crm/jobs?since=${cursor}`, { token }));
    expect(pulled.jobs).toHaveLength(1);
    expect(pulled.jobs[0]).toMatchObject({ externalId: "J1", clientName: "Dana Ruiz" });
    const rows = await env.DB.prepare(`SELECT COUNT(*) AS n FROM crm_jobs WHERE account_id = ?1`).bind(account.id).first<{ n: number }>();
    expect(rows!.n).toBe(2);
  });

  it("form-encoded posts work too (some CRMs send forms)", async () => {
    const { token, account } = await signIn("pro");
    const res = await call("POST", hookPath(account.inboundUrl), {
      raw: "job_number=F-7&customer_name=Ann+Lee&address=1+Main+St",
      headers: { "content-type": "application/x-www-form-urlencoded" },
    });
    expect(res.status).toBe(200);
    const pulled = await json(await call("GET", "/v1/crm/jobs", { token }));
    expect(pulled.jobs[0]).toMatchObject({ externalId: "F-7", clientName: "Ann Lee", address: "1 Main St" });
  });

  it("cancelling a job reaches the phone", async () => {
    const { token, account } = await signIn("pro");
    const hook = hookPath(account.inboundUrl);
    await call("POST", hook, { body: { id: "C1", client_name: "Bo" } });
    await call("POST", hook, { body: { id: "C1", status: "cancelled" } });
    const pulled = await json(await call("GET", "/v1/crm/jobs", { token }));
    expect(pulled.jobs).toHaveLength(1);
    expect(pulled.jobs[0].cancelled).toBe(true);
  });

  it("refuses jobs without an id or a client", async () => {
    const { account } = await signIn("pro");
    const hook = hookPath(account.inboundUrl);
    expect((await call("POST", hook, { body: { client_name: "No id" } })).status).toBe(422);
    expect((await call("POST", hook, { body: { id: "1" } })).status).toBe(422);
    expect((await call("POST", hook, { raw: "not json", headers: { "content-type": "application/json" } })).status).toBe(400);
  });

  it("a rotated address stops the old one working", async () => {
    const { token, account } = await signIn("pro");
    const { inboundUrl } = await json(await call("POST", "/v1/crm/rotate", { token }));
    expect((await call("POST", hookPath(account.inboundUrl), { body: { id: "1", client_name: "A" } })).status).toBe(404);
    expect((await call("POST", hookPath(inboundUrl), { body: { id: "1", client_name: "A" } })).status).toBe(200);
  });

  it("at most 200 jobs a day", async () => {
    const { account } = await signIn("pro");
    await env.DB.prepare(`INSERT INTO counters (account_id, name, period, count) VALUES (?1, 'crm', ?2, 200)`)
      .bind(account.id, new Date().toISOString().slice(0, 10)).run();
    expect((await call("POST", hookPath(account.inboundUrl), { body: { id: "1", client_name: "A" } })).status).toBe(429);
  });
});
