// Renders approximate previews of the place data with headless Chromium.
// Needs `npm install three@0.169.0 playwright` next to this file (not committed).
//
//   lune run build/build.luau --dump <dataDir>
//   node tools/preview/render.mjs <dataDir> <outDir> <path/to/three.module.js>
//
// <dataDir> holds scene.json / ui.json from `lune run build/build.luau --dump <dataDir>`.
// Output PNGs are APPROXIMATE PREVIEWS (watermarked), not Roblox screenshots.

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const [dataDir, outDir, threePath] = process.argv.slice(2);
const here = path.dirname(new URL(import.meta.url).pathname);
const files = {
  "/": path.join(here, "preview.html"),
  "/three.module.js": threePath,
  "/scene.json": path.join(dataDir, "scene.json"),
  "/ui.json": path.join(dataDir, "ui.json"),
};
const server = http.createServer((req, res) => {
  const file = files[req.url];
  if (!file) { res.writeHead(404); return res.end(); }
  const type = req.url.endsWith(".js") ? "text/javascript" : req.url.endsWith(".json") ? "application/json" : "text/html";
  res.writeHead(200, { "content-type": type });
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, r));
const port = server.address().port;

const TEXT = "EEE9DC", ACCENT = "D6AD62";
const deliver102 = {
  "Device/Body/Instruction": { Text: "Deliver to Apartment 102." },
  "Device/Body/Steps/StepPickup": { TextColor3: TEXT, TextTransparency: 0.55 },
  "Device/Body/Steps/StepDeliver": { TextColor3: ACCENT, TextTransparency: 0 },
};
const next103 = {
  "Device/Body/Meta/Counter": { Text: "2 / 3" },
  "Device/Body/Target": { Text: "Apartment 103" },
  "Earnings/Value": { Text: "$25" },
  "Toast": { Visible: true },
  "Toast/Panel/Content/Title": { Text: "Delivery complete" },
  "Toast/Panel/Content/Detail": { Text: "+$25 · Apartment 102" },
};
const prompt102 = { template: "PromptBillboard", pos: [27, 3.6, -5.6], overrides: { "Button/Object": { Text: "Apartment 102" }, "Button/Action": { Text: "Deliver parcel" }, "Button/Progress": { Size: [0.55, -8, 0, 2] } } };
const markerAt = (pos, text) => ({ template: "TargetMarker", pos, overrides: { "Label": { Text: text } } });

const views = [
  {
    file: "01_elevator_to_corridor.png", width: 1600, height: 900, hud: true, chat: true, doorsOpen: true,
    caption: "Spawn in the cab, doors open, first delivery on the device",
    characters: [{ x: -4.5, z: 0, yaw: -Math.PI / 2 }],
    camera: { pos: [-10.5, 6.4, 0.7], look: [8, 4.2, 0] },
    billboards: [markerAt([4.4, 3.9, -5.25], "PARCELS")],
  },
  {
    file: "02_door_and_parcel_player_height.png", width: 1600, height: 900, hud: true, chat: true, doorsOpen: true,
    caption: "Carrying the parcel at door 102 (camera pushed in by the opposite wall)",
    characters: [{ x: 27, z: -2.4, yaw: 0, parcel: true }],
    camera: { pos: [28.3, 7.0, 6.0], look: [26.8, 4.2, -4.5] },
    billboards: [prompt102], overrides: deliver102,
  },
  {
    file: "03_desktop_device_open_1920x1080.png", width: 1920, height: 1080, hud: true, chat: true, doorsOpen: true,
    caption: "Desktop 1920x1080, device open, heading to 102",
    characters: [{ x: 14, z: 0.4, yaw: -Math.PI / 2, parcel: true }],
    camera: { pos: [3.4, 7.6, 0.6], look: [26, 4.4, 0] },
    billboards: [markerAt([27, 9.2, -6.1], "102")], overrides: deliver102,
  },
  {
    file: "04_mobile_844x390_device_open.png", width: 844, height: 390, hud: true, touch: true, doorsOpen: true,
    caption: "Phone landscape 844x390 (UIScale 0.85), device open",
    characters: [{ x: 14, z: 0.4, yaw: -Math.PI / 2, parcel: true }],
    camera: { pos: [2.6, 7.6, 1.6], look: [26, 4.4, 0] },
    billboards: [markerAt([27, 9.2, -6.1], "102")],
    overrides: { ...deliver102, "Device/Header/Key/Label": { Text: "HIDE" } },
  },
  {
    file: "04b_mobile_844x390_device_collapsed.png", width: 844, height: 390, hud: true, touch: true, doorsOpen: true,
    caption: "Phone landscape, device folded to its header (auto after 6 s on small screens)",
    characters: [{ x: 14, z: 0.4, yaw: -Math.PI / 2, parcel: true }],
    camera: { pos: [2.6, 7.6, 1.6], look: [26, 4.4, 0] },
    billboards: [markerAt([27, 9.2, -6.1], "102")],
    overrides: {
      ...deliver102, "Device/Header/Key/Label": { Text: "SHOW" }, "Device/Body": { Visible: false }, "Device/Divider": { Visible: false },
      "Device/Header/Summary": { Visible: true, Text: "Apt 102" },
    },
  },
  {
    file: "05_delivery_complete.png", width: 1600, height: 900, hud: true, chat: true, doorsOpen: true,
    caption: "Delivery accepted: parcel set on the mat, toast, earnings +$25, next delivery shown",
    characters: [{ x: 29.6, z: -1.6, yaw: 0.5 }],
    ghosts: [{ x: 26.4, y: 0.515, z: -5.4, yaw: 0.12 }],
    camera: { pos: [28.3, 7.0, 6.0], look: [26.8, 4.2, -4.5] },
    overrides: next103,
  },
  {
    file: "06_corridor_back_to_elevator.png", width: 1600, height: 900, hud: false, doorsOpen: false,
    caption: "From the window end back to the elevator, eye height 5 (no HUD)",
    camera: { pos: [48.5, 5.0, 1.2], look: [0, 4.3, -0.3] },
  },
  {
    file: "07_doors_103_101_player_height.png", width: 1600, height: 900, hud: false, doorsOpen: true, showScaleRef: true,
    caption: "North wall: 103, notice board, 101 with the R15 scale reference (edit-only) in front of 101",
    camera: { pos: [44.5, 5.2, -4.8], look: [30, 4.4, 5.2] },
  },
];

const browser = await chromium.launch({ args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
fs.mkdirSync(outDir, { recursive: true });
for (const v of views) {
  const page = await browser.newPage({ viewport: { width: v.width, height: v.height } });
  page.on("console", m => { if (m.type() === "error") console.error("page:", m.text()); });
  page.on("pageerror", e => console.error("pageerror:", e.message));
  await page.goto(`http://localhost:${port}/`);
  await page.waitForFunction(() => window.ready === true, null, { timeout: 60000 });
  await page.evaluate(cfg => window.renderView(cfg), v);
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(outDir, v.file) });
  await page.close();
  console.log("rendered", v.file);
}
await browser.close();
server.close();
