import test from "node:test";
import assert from "node:assert/strict";
import { evaluatePreview } from "../src/model.js";

const input = {
  pumpFlowLMin: 4,
  hemoglobinGdL: 10,
  arterialSaturation: 0.98,
  systemicVascularResistance: 1200,
  bodySurfaceAreaM2: 1.8,
  temperatureC: 35
};

test("calculates the same educational preview values as the Java model", () => {
  const result = evaluatePreview(input, new Date("2026-01-01T00:00:00.000Z"));
  assert.equal(result.estimatedMapMmHg, 60);
  assert.equal(result.estimatedOxygenDeliveryMlMin, 525.28);
  assert.equal(result.estimatedOxygenDeliveryIndexMlMinM2, 291.82);
  assert.equal(result.modelVersion, "0.1.0-educational");
});

test("rejects missing, non-numeric and out-of-range values", () => {
  assert.throws(() => evaluatePreview({ ...input, pumpFlowLMin: 0 }), error => {
    assert.equal(error.statusCode, 400);
    assert.equal(error.details[0].field, "pumpFlowLMin");
    return true;
  });
  assert.throws(() => evaluatePreview({ ...input, temperatureC: "35" }), error => {
    assert.equal(error.statusCode, 400);
    return true;
  });
});
