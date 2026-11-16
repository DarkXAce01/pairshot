// Runs the real client page in a simulated browser with sample data.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";

const template = readFileSync(new URL("../gallery.html", import.meta.url), "utf8");

function render(data) {
  const json = JSON.stringify(data).replace(/</g, "\\u003c");
  const html = template.replace("/*DATA*/", json).replace("<!--META-->", "");
  const errors = [];
  const dom = new JSDOM(html, { runScripts: "dangerously", pretendToBeVisual: true });
  dom.window.addEventListener("error", (e) => errors.push(e.message));
  return { doc: dom.window.document, win: dom.window, errors };
}

const img = (n) => ({ src: `/p/${n}.jpg`, w: 3, h: 4 });
const pair = (id, label) => ({ id, label, before: img(`b-${id}`), after: img(`a-${id}`) });
const base = {
  v: 1,
  business: { name: "Shine Bros", phone: "555 0100", reviewUrl: "https://g.page/r/x" },
  job: { title: "House wash", place: "12 Oak St", date: "12 November 2026" },
  cover: pair("c"),
  sections: [{ pairs: [pair("u1")] }, { label: "Driveway", pairs: [pair("d1", "Driveway"), pair("d2", "Driveway")] }],
  badge: true,
};

test("renders the hero, sections and every slider without errors", () => {
  const { doc, errors } = render(base);
  assert.deepEqual(errors, []);
  assert.equal(doc.querySelector("h1").textContent, "House wash");
  assert.equal(doc.querySelector(".meta").textContent, "12 Oak St · 12 November 2026");
  assert.equal(doc.querySelectorAll(".ba").length, 4);
  assert.deepEqual([...doc.querySelectorAll("h2")].map((h) => h.textContent), ["More", "Driveway"]);
  assert.equal(doc.querySelector(".ba img.before").getAttribute("src"), "/p/b-c.jpg");
  assert.equal(doc.title, "House wash · Shine Bros");
});

test("only the buttons the business filled in appear", () => {
  const { doc } = render(base);
  const labels = [...doc.querySelectorAll(".btn")].map((a) => a.textContent);
  assert.deepEqual(labels, ["Leave a review", "Call Shine Bros"]);
  assert.equal(doc.querySelector(".btn[href^='tel:']").getAttribute("href"), "tel:5550100");
});

test("badge shows on free and not on pro", () => {
  assert.ok(render(base).doc.querySelector(".badge"));
  assert.equal(render({ ...base, badge: false }).doc.querySelector(".badge"), null);
});

test("text from the business can't inject HTML", () => {
  const { doc } = render({ ...base, business: { name: "<img src=x onerror=alert(1)>" }, job: { title: "<b>x</b>" } });
  assert.equal(doc.querySelector("img[src='x']"), null);
  assert.equal(doc.querySelector("h1").textContent, "<b>x</b>");
});

test("unsafe links are dropped", () => {
  const { doc } = render({ ...base, business: { name: "A", reviewUrl: "javascript:alert(1)", bookingUrl: "https://book.me" } });
  const hrefs = [...doc.querySelectorAll(".btn")].map((a) => a.getAttribute("href"));
  assert.deepEqual(hrefs, ["https://book.me"]);
});

test("the slider moves with the keyboard/range control", () => {
  const { doc, win } = render(base);
  const box = doc.querySelector(".ba");
  const range = box.querySelector("input[type=range]");
  range.value = "20";
  range.dispatchEvent(new win.Event("input"));
  assert.equal(box.style.getPropertyValue("--pos"), "20%");
});

test("missing or broken data shows a calm message instead of a broken page", () => {
  const { doc, errors } = render({ ...base, cover: null });
  assert.deepEqual(errors, []);
  assert.match(doc.querySelector(".empty").textContent, /aren't ready yet/);
});

test("a bad accent colour is ignored", () => {
  const { doc } = render({ ...base, business: { name: "A", accent: "red;background:url(x)" } });
  assert.equal(doc.documentElement.style.getPropertyValue("--accent"), "");
});

// ---- Page styles ----

const styles = ["classic", "showroom", "bright", "report"];

for (const t of styles) {
  test(`${t}: renders hero, sections, buttons and badge without errors`, () => {
    const { doc, errors } = render({ ...base, template: t });
    assert.deepEqual(errors, []);
    assert.equal(doc.documentElement.getAttribute("data-template"), t);
    assert.equal(doc.querySelector("h1").textContent, "House wash");
    assert.deepEqual([...doc.querySelectorAll("h2")].map((h) => h.textContent), ["More", "Driveway"]);
    assert.equal(doc.querySelectorAll(".pair").length, 4);
    assert.deepEqual([...doc.querySelectorAll(".btn")].map((a) => a.textContent), ["Leave a review", "Call Shine Bros"]);
    assert.ok(doc.querySelector(".badge"));
    assert.ok(doc.querySelector(".biz").textContent.includes("Shine Bros"));
  });
}

test("an unknown or odd template falls back to Classic", () => {
  for (const t of ["fancy", "constructor", "__proto__", 7, null]) {
    const { doc, errors } = render({ ...base, template: t });
    assert.deepEqual(errors, []);
    assert.equal(doc.documentElement.getAttribute("data-template"), "classic");
  }
  assert.equal(render(base).doc.documentElement.getAttribute("data-template"), "classic");
});

test("text on the accent is dark on a light accent and white on a dark one", () => {
  const light = render({ ...base, business: { ...base.business, accent: "#F5E663" } }).doc;
  assert.equal(light.documentElement.style.getPropertyValue("--on-accent"), "#111111");
  const dark = render({ ...base, business: { ...base.business, accent: "#1E3A5F" } }).doc;
  assert.equal(dark.documentElement.style.getPropertyValue("--on-accent"), "#fff");
  assert.equal(dark.documentElement.style.getPropertyValue("--accent"), "#1E3A5F");
});

test("an accent that vanishes into the page gets an edge", () => {
  const white = render({ ...base, template: "bright", business: { ...base.business, accent: "#FFFFFF" } }).doc;
  assert.ok(white.documentElement.hasAttribute("data-edge"));
  const ink = render({ ...base, template: "showroom", business: { ...base.business, accent: "#1C1B19" } }).doc;
  assert.ok(ink.documentElement.hasAttribute("data-edge"));
  const navy = render({ ...base, template: "bright", business: { ...base.business, accent: "#1E3A5F" } }).doc;
  assert.equal(navy.documentElement.hasAttribute("data-edge"), false);
});

test("with an edge, focus rings and the Report and Bright rules switch to ink", () => {
  const yellow = render({ ...base, template: "report", business: { ...base.business, accent: "#F5E663" } }).doc;
  assert.ok(yellow.documentElement.hasAttribute("data-edge"));
  const css = yellow.querySelector("style").textContent;
  for (const sel of [
    "html[data-edge] .ba:focus-within",
    "html[data-edge] .expand:focus-visible",
    "html[data-edge] .mode button:focus-visible",
    'html[data-edge][data-template="report"] header.top { border-bottom-color: var(--ink); }',
    'html[data-edge][data-template="bright"] body { border-top-color: var(--ink); }',
    ".viewer button:focus-visible { outline-color: #fff; }",
  ]) assert.ok(css.includes(sel), sel);
});

test("Report does not repeat the job title in its facts", () => {
  const report = render({ ...base, template: "report" }).doc;
  const title = report.querySelector("h1").textContent;
  assert.ok(![...report.querySelectorAll(".facts dd")].some((d) => d.textContent === title));
});

test("Report starts side by side with captions and a facts table; the others start on the slider", () => {
  const report = render({ ...base, template: "report" }).doc;
  assert.equal(report.querySelectorAll(".ba").length, 0);
  assert.equal(report.querySelectorAll(".sbs").length, 4);
  assert.deepEqual([...report.querySelectorAll(".facts dt")].map((d) => d.textContent), ["Location", "Date", "Photos"]);
  assert.equal(report.querySelectorAll(".facts dd")[2].textContent, "8");
  assert.deepEqual([...report.querySelectorAll(".cap")].map((c) => c.textContent), ["House wash", "Driveway", "Driveway"]);
  for (const t of ["classic", "showroom", "bright"]) assert.equal(render({ ...base, template: t }).doc.querySelectorAll(".ba").length, 4);
});

test("the compare choice is remembered per style", () => {
  const json = JSON.stringify({ ...base, template: "bright" }).replace(/</g, "\\u003c");
  const html = template.replace("/*DATA*/", json).replace("<!--META-->", "");
  const dom = new JSDOM(html, { runScripts: "dangerously", url: "https://pairshot.test/g/x" });
  const doc = dom.window.document;
  [...doc.querySelectorAll(".mode button")][1].click();
  assert.equal(dom.window.localStorage.getItem("pairshot-mode-bright"), "sbs");
  assert.equal(dom.window.localStorage.getItem("pairshot-mode"), null);
  assert.equal(doc.querySelectorAll(".sbs").length, 4);
});

test("Bright shows a monogram and Showroom puts the cover above the headline", () => {
  const bright = render({ ...base, template: "bright" }).doc;
  assert.equal(bright.querySelector(".monogram").textContent, "S");
  const show = render({ ...base, template: "showroom" }).doc;
  const cover = show.querySelector(".cover-box"), h1 = show.querySelector("h1");
  assert.ok(cover.compareDocumentPosition(h1) & 4); // h1 follows the cover
});

test("business text can't inject HTML in any style", () => {
  for (const t of styles) {
    const { doc } = render({ ...base, template: t, business: { name: "<img src=x onerror=alert(1)>" }, job: { title: "<b>x</b>", place: "<i>y</i>" } });
    assert.equal(doc.querySelector("img[src='x']"), null);
    assert.equal(doc.querySelector("b"), null);
    assert.equal(doc.querySelector("h1").textContent, "<b>x</b>");
  }
});

test("Download all photos shows only when the page has a download link, in every style", () => {
  for (const t of styles) {
    assert.equal(render({ ...base, template: t }).doc.querySelector("a[download]"), null);
    const { doc } = render({ ...base, template: t, download: "https://pairshot.test/g/x/zip" });
    assert.equal(doc.querySelector("a[download]").textContent, "Download all photos");
  }
});
