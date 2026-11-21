export const MODEL_VERSION = "0.1.0-educational";

export const LIMITS = {
  pumpFlowLMin: [0.1, 10.0],
  hemoglobinGdL: [1.0, 25.0],
  arterialSaturation: [0.5, 1.0],
  systemicVascularResistance: [100.0, 5000.0],
  bodySurfaceAreaM2: [0.3, 3.0],
  temperatureC: [15.0, 40.0]
};

export function evaluatePreview(input, now = new Date()) {
  const errors = [];
  for (const [field, [min, max]] of Object.entries(LIMITS)) {
    const value = input?.[field];
    if (typeof value !== "number" || !Number.isFinite(value)) {
      errors.push({ field, message: "must be a finite number" });
    } else if (value < min || value > max) {
      errors.push({ field, message: `must be between ${min} and ${max}` });
    }
  }
  if (errors.length) {
    const error = new Error("Invalid simulation parameters");
    error.statusCode = 400;
    error.details = errors;
    throw error;
  }

  const estimatedMapMmHg =
    input.pumpFlowLMin * input.systemicVascularResistance / 80;
  const estimatedOxygenDeliveryMlMin =
    input.pumpFlowLMin * 10 * input.hemoglobinGdL * 1.34 * input.arterialSaturation;
  const estimatedOxygenDeliveryIndexMlMinM2 =
    estimatedOxygenDeliveryMlMin / input.bodySurfaceAreaM2;
  const round = value => Math.round((value + Number.EPSILON) * 100) / 100;

  return {
    modelVersion: MODEL_VERSION,
    generatedAt: now.toISOString(),
    estimatedMapMmHg: round(estimatedMapMmHg),
    estimatedOxygenDeliveryMlMin: round(estimatedOxygenDeliveryMlMin),
    estimatedOxygenDeliveryIndexMlMinM2: round(estimatedOxygenDeliveryIndexMlMinM2),
    pumpFlowLMin: input.pumpFlowLMin,
    temperatureC: input.temperatureC,
    interpretation: "Educational calculation only. No clinical interpretation or treatment recommendation."
  };
}
