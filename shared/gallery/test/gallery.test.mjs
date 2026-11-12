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
