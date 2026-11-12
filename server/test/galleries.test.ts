import { describe, expect, it } from "vitest";
import { call, env, json, makePairs, send, sendBody, signIn, upload } from "./helpers";

const slugOf = (url: string) => new URL(url).pathname.split("/").pop()!;

describe("sending a gallery", () => {
  it("prepare lists only the photos still to upload", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 2);
    const missingHash = "a".repeat(64);
    pairs.push({ id: "x", before: missingHash, after: pairs[0].after });
    const res = await call("POST", `/v1/galleries/${crypto.randomUUID()}/prepare`, { token, body: sendBody("job-test-1", pairs) });
    expect(res.status).toBe(200);
    expect((await json(res)).missing).toEqual([missingHash]);
  });

  it("won't publish until every photo is uploaded", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 1);
    pairs[0].after = "b".repeat(64);
    const { res, body } = await send(token, pairs);
    expect(res.status).toBe(409);
    expect(body.error).toBe("photos_missing");
  });

  it("refuses a gallery with no finished pairs", async () => {
    const { token } = await signIn();
    const { res } = await send(token, []);
    expect(res.status).toBe(400);
  });

  it("publishes and serves the client page with the photos", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 3, "Driveway");
    const { res, body } = await send(token, pairs);
    expect(res.status).toBe(201);
    expect(body.state).toBe("live");
    expect(body.url).toMatch(/^https:\/\/pairshot\.test\/g\/[A-Za-z0-9]{12}$/);
    expect(body.expiresAt - body.publishedAt).toBe(14 * 86_400_000);

    const page = await call("GET", new URL(body.url).pathname, { headers: { "user-agent": "Mozilla/5.0 (Linux; Android 15) Chrome/130" } });
    expect(page.status).toBe(200);
    const html = await page.text();
    expect(html).toContain(`/i/${account.id}/${pairs[0].after}`);
    expect(html).toContain('<meta property="og:image"');
    expect(html).toContain("House wash · Shine Bros");
    expect(html).not.toContain("Dana"); // client details never reach the page
    expect(page.headers.get("content-security-policy")).toContain("script-src 'sha256-");
    expect(page.headers.get("x-robots-tag")).toContain("noindex");
    // Free plan always shows the badge, whatever the phone said.
    expect(html).toContain('"badge":true');

    const photo = await call("GET", `/i/${account.id}/${pairs[1].before}`);
    expect(photo.status).toBe(200);
    expect(photo.headers.get("content-type")).toBe("image/jpeg");
  });

  it("text in the gallery can't break out of the page", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 1);
    const { body } = await send(token, pairs, { over: { job: { title: "</script><script>alert(1)</script>" } } });
    const html = await (await call("GET", new URL(body.url).pathname)).text();
    expect(html).not.toContain("</script><script>alert(1)");
  });

  it("photos of other accounts or unused photos are never served", async () => {
    const a = await signIn();
    const loose = await upload(a.token);
    expect((await call("GET", `/i/${a.account.id}/${loose}`)).status).toBe(404);
    const pairs = await makePairs(a.token, 1);
    await send(a.token, pairs);
    const b = await signIn();
    expect((await call("GET", `/i/${b.account.id}/${pairs[0].before}`)).status).toBe(404);
  });

  it("free plan: 3 galleries a month, and closing one doesn't give it back", async () => {
    const { token } = await signIn();
    const ids: string[] = [];
    for (let i = 0; i < 3; i++) {
      const { res, id } = await send(token, await makePairs(token, 1));
      expect(res.status).toBe(201);
      ids.push(id);
    }
    await call("DELETE", `/v1/galleries/${ids[0]}`, { token });
    const { res, body } = await send(token, await makePairs(token, 1));
    expect(res.status).toBe(402);
    expect(body.error).toBe("monthly_limit");
    const usage = await json(await call("GET", "/v1/usage", { token }));
    expect(usage.galleriesThisMonth).toBe(3);
    expect(usage.galleriesPerMonth).toBe(3);
  });

  it("pro has no monthly limit and galleries never expire", async () => {
    const { token } = await signIn("pro");
    for (let i = 0; i < 4; i++) expect((await send(token, await makePairs(token, 1))).res.status).toBe(201);
    const { body } = await send(token, await makePairs(token, 1));
    expect(body.expiresAt).toBeNull();
    const page = await (await call("GET", new URL(body.url).pathname)).text();
    expect(page).toContain('"badge":false');
  });

  it("photos per gallery: 30 on free, 100 on pro", async () => {
    const free = await signIn();
    const sixteen = await makePairs(free.token, 16); // 32 photos
    const { res, body } = await send(free.token, sixteen);
    expect(res.status).toBe(422);
    expect(body.error).toBe("too_many_photos");
    expect((await send(free.token, sixteen.slice(0, 15))).res.status).toBe(201);

    const pro = await signIn("pro");
    expect((await send(pro.token, await makePairs(pro.token, 16))).res.status).toBe(201);
  });
});

describe("updating a sent gallery", () => {
  it("keeps the same link, and a reorder uploads nothing", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 3);
    const first = await send(token, pairs, { jobId: "job-test-u1" });
    const reordered = [pairs[2], pairs[0], pairs[1]];
    const prep = await json(await call("POST", `/v1/galleries/${first.id}/prepare`, { token, body: sendBody("job-test-u1", reordered) }));
    expect(prep.missing).toEqual([]);
    const second = await send(token, reordered, { id: first.id, jobId: "job-test-u1" });
    expect(second.res.status).toBe(200);
    expect(second.body.url).toBe(first.body.url);
    expect(second.body.expiresAt).toBe(first.body.expiresAt); // expiry never resets
    // An update doesn't use up a monthly gallery.
    expect((await json(await call("GET", "/v1/usage", { token }))).galleriesThisMonth).toBe(1);
  });

  it("removed photos stop being served", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 2);
    const first = await send(token, pairs, { jobId: "job-test-u2" });
    await send(token, [pairs[0]], { id: first.id, jobId: "job-test-u2" });
    expect((await call("GET", `/i/${account.id}/${pairs[1].before}`)).status).toBe(404);
  });

  it("free: only for 3 days after sending; pro: any time", async () => {
    for (const plan of ["free", "pro"] as const) {
      const { token } = await signIn(plan);
      const pairs = await makePairs(token, 1);
      const first = await send(token, pairs, { jobId: "job-test-w" });
      await env.DB.prepare(`UPDATE galleries SET published_at = published_at - ?2 WHERE id = ?1`).bind(first.id, 4 * 86_400_000).run();
      const again = await send(token, pairs, { id: first.id, jobId: "job-test-w" });
      expect(again.res.status).toBe(plan === "free" ? 403 : 200);
      if (plan === "free") expect(again.body.error).toBe("edit_window");
    }
  });

  it("free: the job's title and place are locked; pro may change them", async () => {
    for (const plan of ["free", "pro"] as const) {
      const { token } = await signIn(plan);
      const pairs = await makePairs(token, 1);
      const first = await send(token, pairs, { jobId: "job-test-l" });
      const other = await send(token, pairs, { id: first.id, jobId: "job-test-l", over: { job: { title: "Roof clean", place: "99 Elm Rd" } } });
      expect(other.res.status).toBe(plan === "free" ? 403 : 200);
    }
  });

  it("a gallery can't be moved to another job", async () => {
    const { token } = await signIn();
    const pairs = await makePairs(token, 1);
    const first = await send(token, pairs, { jobId: "job-test-a" });
    expect((await send(token, pairs, { id: first.id, jobId: "job-test-b" })).res.status).toBe(409);
  });

  it("at most 20 updates a day", async () => {
    const { token } = await signIn("pro");
    const pairs = await makePairs(token, 1);
    const first = await send(token, pairs, { jobId: "job-test-r" });
    await env.DB.prepare(`INSERT INTO counters (account_id, name, period, count) VALUES (?1, 'updates', ?2, 20)`)
      .bind(first.id, new Date().toISOString().slice(0, 10)).run();
    const res = await send(token, pairs, { id: first.id, jobId: "job-test-r" });
    expect(res.res.status).toBe(429);
  });

  it("one link can't be reused for job after job (photo history cap)", async () => {
    const { token } = await signIn();
    const first = await send(token, await makePairs(token, 15), { jobId: "job-test-h" }); // 30 photos
    const second = await send(token, await makePairs(token, 8), { id: first.id, jobId: "job-test-h" }); // 30 + 16 = 46 > 45
    expect(second.res.status).toBe(403);
    expect(second.body.error).toBe("reused");
  });
});

describe("closing, expiry and views", () => {
  it("closing a link shows a polite page and deletes its photos", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 1);
    const { id, body } = await send(token, pairs);
    expect((await call("DELETE", `/v1/galleries/${id}`, { token })).status).toBe(200);
    const page = await call("GET", new URL(body.url).pathname);
    expect(page.status).toBe(410);
    expect(await page.text()).toContain("Shine Bros");
    expect(await env.PHOTOS.head(`p/${account.id}/${pairs[0].before}`)).toBeNull();
    expect((await send(token, pairs, { id, jobId: `job-${id}` })).res.status).toBe(410);
  });

  it("an expired gallery is closed", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 1);
    const { id, body } = await send(token, pairs);
    await env.DB.prepare(`UPDATE galleries SET expires_at = ?2 WHERE id = ?1`).bind(id, Date.now() - 1000).run();
    expect((await call("GET", new URL(body.url).pathname)).status).toBe(410);
    expect((await call("GET", `/i/${account.id}/${pairs[0].before}`)).status).toBe(404);
  });

  it("an unknown link says so", async () => {
    expect((await call("GET", "/g/doesnotexist")).status).toBe(404);
  });

  it("counts real views, not link-preview bots, and marks the first", async () => {
    const { token } = await signIn();
    const { body } = await send(token, await makePairs(token, 1));
    const path = new URL(body.url).pathname;
    await call("GET", path, { headers: { "user-agent": "WhatsApp/2.23" } });
    await call("GET", path, { headers: { "user-agent": "facebookexternalhit/1.1" } });
    let list = (await json(await call("GET", "/v1/galleries", { token }))).galleries;
    expect(list[0].viewCount).toBe(0);
    expect(list[0].firstViewedAt).toBeNull();
    await call("GET", path, { headers: { "user-agent": "Mozilla/5.0 (iPhone) Safari" } });
    await call("GET", path, { headers: { "user-agent": "Mozilla/5.0 (iPhone) Safari" } });
    list = (await json(await call("GET", "/v1/galleries", { token }))).galleries;
    expect(list[0].viewCount).toBe(2);
    expect(list[0].firstViewedAt).toBeGreaterThan(0);
  });

  it("deleting the account deletes everything", async () => {
    const { token, account } = await signIn();
    const pairs = await makePairs(token, 1);
    const { body } = await send(token, pairs);
    expect((await call("DELETE", "/v1/me", { token })).status).toBe(200);
    expect(await env.PHOTOS.head(`p/${account.id}/${pairs[0].before}`)).toBeNull();
    expect((await call("GET", new URL(body.url).pathname)).status).toBe(404);
    expect((await call("GET", "/v1/me", { token })).status).toBe(401);
  });
});
