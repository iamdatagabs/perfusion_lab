import test from "node:test";
import assert from "node:assert/strict";
import { calculateMetrics, calculateDeviation, classifyDeviation } from "../assets/js/simulator.js";

test("calculates the homepage simulator default indicators", () => {
  const result = calculateMetrics({ flow: 4.8, hb: 10, sat: 98, svr: 1200, bsa: 1.9 });
  assert.equal(result.map, 72);
  assert.equal(Number(result.do2.toFixed(2)), 630.34);
  assert.equal(Number(result.do2i.toFixed(2)), 331.76);
});

test("indicator is zero against its own reference and rises when parameters change", () => {
  const reference = calculateMetrics({ flow: 4.8, hb: 10, sat: 98, svr: 1200, bsa: 1.9 });
  assert.equal(calculateDeviation(reference, reference).score, 0);
  const changed = calculateMetrics({ flow: 5.8, hb: 10, sat: 98, svr: 1200, bsa: 1.9 });
  assert.ok(calculateDeviation(changed, reference).score > 0);
});

test("classifies stable, intermediate and critical mathematical deviations", () => {
  assert.equal(classifyDeviation(5, 10, 25), "stable");
  assert.equal(classifyDeviation(15, 10, 25), "unstable");
  assert.equal(classifyDeviation(30, 10, 25), "critical");
  assert.throws(() => classifyDeviation(10, 25, 10), RangeError);
});
