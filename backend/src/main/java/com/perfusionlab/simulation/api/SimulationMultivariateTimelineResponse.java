package com.perfusionlab.simulation.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
public record SimulationMultivariateTimelineResponse(
 String modelVersion, Instant generatedAt, int durationSeconds, int stepSeconds,
 List<String> parameters, List<Point> points, String interpretation
) {
 public record Point(int elapsedSeconds, Map<String,Double> parameterValues,
  double estimatedMapMmHg, double estimatedOxygenDeliveryMlMin,
  double estimatedOxygenDeliveryIndexMlMinM2) {}
}