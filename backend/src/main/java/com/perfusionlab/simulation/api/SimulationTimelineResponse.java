package com.perfusionlab.simulation.api;
import java.time.Instant;
import java.util.List;
public record SimulationTimelineResponse(String modelVersion, Instant generatedAt, String parameter,
 int durationSeconds, int stepSeconds, List<SimulationTimelinePoint> points, String interpretation) {
 public record SimulationTimelinePoint(int elapsedSeconds, double parameterValue, double estimatedMapMmHg,
  double estimatedOxygenDeliveryMlMin, double estimatedOxygenDeliveryIndexMlMinM2) {}
}