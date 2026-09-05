import assert from "node:assert/strict";
import { anchorPlacementsToFields } from "./placement-anchoring.ts";

const document = { pages: [{ width: 595, height: 842 }] };
const hints = [
  {
    field_id: "p1_f001",
    page_index: 0,
    type: "line",
    x: 0.20,
    y: 0.40,
    width: 0.30,
    content_width: 0.28,
    height: 0.03,
    anchor_x: 0.205,
    baseline_y: 0.423,
    recommended_size: 8,
  },
  {
    field_id: "p1_f002",
    page_index: 0,
    type: "checkbox",
    x: 0.70,
    y: 0.20,
    width: 0.015,
    height: 0.012,
    mark_x: 0.7075,
    mark_y: 0.206,
    recommended_size: 5,
  },
];

const exact = anchorPlacementsToFields(
  [{ field_id: "p1_f001" }],
  [{ overlay_id: "name", page_index: 0, x: 0.9, y: 0.9, text: "Nom très long", size: 20, kind: "text" }],
  hints,
  document,
);
assert.equal(exact.placements[0].x, 0.205);
assert.equal(exact.placements[0].y, 0.423);
assert.equal(exact.placements[0].anchored_to_field, true);
assert.ok(exact.placements[0].size <= 8);
assert.equal(exact.report[0].mode, "field_id");

const checkbox = anchorPlacementsToFields(
  [{}],
  [{ overlay_id: "choice", page_index: 0, x: 0.705, y: 0.207, text: "X", size: 8, kind: "text" }],
  hints,
  document,
);
assert.equal(checkbox.placements[0].kind, "checkbox");
assert.equal(checkbox.placements[0].align, "center");
assert.equal(checkbox.placements[0].x, 0.7075);
assert.equal(checkbox.placements[0].size, 5);
assert.equal(checkbox.report[0].mode, "nearby_unique_field");

const backwardCompatible = anchorPlacementsToFields(
  [{}],
  [{ overlay_id: "free", page_index: 0, x: 0.1, y: 0.1, text: "Libre", size: 8 }],
  [],
  document,
);
assert.equal(backwardCompatible.placements[0].x, 0.1);
assert.equal(backwardCompatible.report.length, 0);

const ambiguousHints = [
  { ...hints[0], field_id: "p1_f010", anchor_x: 0.30, baseline_y: 0.50 },
  { ...hints[0], field_id: "p1_f011", anchor_x: 0.306, baseline_y: 0.50 },
];
const ambiguous = anchorPlacementsToFields(
  [{}],
  [{ overlay_id: "ambiguous", page_index: 0, x: 0.303, y: 0.50, text: "Valeur", size: 8 }],
  ambiguousHints,
  document,
);
assert.equal(ambiguous.report.length, 0);
assert.equal(ambiguous.placements[0].x, 0.303);

assert.throws(() => anchorPlacementsToFields(
  [{ field_id: "missing" }],
  [{ overlay_id: "bad", page_index: 0, x: 0.1, y: 0.1, text: "Valeur", size: 8 }],
  hints,
  document,
), /introuvable/);

console.log("placement anchoring tests: ok");
