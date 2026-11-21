package com.perfusionlab.simulation.domain;
import static org.junit.jupiter.api.Assertions.*;
import com.perfusionlab.simulation.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class EducationalTimelineModelTest {
 private final EducationalTimelineModel model=new EducationalTimelineModel(new SimplifiedPerfusionModel());
 private final SimulationPreviewRequest baseline=new SimulationPreviewRequest(4.0,10.0,.98,1200.0,1.8,35.0);
 @Test void interpolatesOneInputAndReturnsBoundedTimeline(){
  var r=model.evaluate(new SimulationTimelineRequest(baseline,"pumpFlowLMin",6.0,60,15));
  assertEquals(5,r.points().size()); assertEquals(0,r.points().get(0).elapsedSeconds());
  assertEquals(4.0,r.points().get(0).parameterValue(),.001); assertEquals(6.0,r.points().get(4).parameterValue(),.001);
  assertEquals(60,r.points().get(4).elapsedSeconds()); assertEquals(4.0,r.points().get(0).estimatedMapMmHg(),.01);
  assertEquals(6.0,r.points().get(4).estimatedMapMmHg(),.01);
 }
 @Test void appendsExactEndTimeWhenStepDoesNotDivideDuration(){
  var r=model.evaluate(new SimulationTimelineRequest(baseline,"pumpFlowLMin",5.0,50,20));
  assertEquals(4,r.points().size()); assertEquals(50,r.points().get(3).elapsedSeconds());
 }
 @Test void rejectsOutOfRangeEndValue(){assertThrows(ResponseStatusException.class,()->model.evaluate(
  new SimulationTimelineRequest(baseline,"pumpFlowLMin",11.0,60,10)));}
 @Test void rejectsTimelinesWithTooManyPoints(){assertThrows(ResponseStatusException.class,()->model.evaluate(
  new SimulationTimelineRequest(baseline,"pumpFlowLMin",5.0,3600,1)));}
}