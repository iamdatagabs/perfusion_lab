package com.perfusionlab.simulation.domain;
import static org.junit.jupiter.api.Assertions.*;
import com.perfusionlab.simulation.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class EducationalMultivariateTimelineModelTest {
 private final SimplifiedPerfusionModel baseModel=new SimplifiedPerfusionModel();
 private final EducationalTimelineModel validator=new EducationalTimelineModel(baseModel);
 private final EducationalMultivariateTimelineModel model=new EducationalMultivariateTimelineModel(baseModel,validator);
 private final SimulationPreviewRequest baseline=new SimulationPreviewRequest(4.0,10.0,.98,1200.0,1.8,35.0);
 @Test void interpolatesSeveralInputsTogether(){
  var r=model.evaluate(new SimulationMultivariateTimelineRequest(baseline,List.of(
   new SimulationMultivariateTimelineRequest.ParameterRamp("pumpFlowLMin",6.0),
   new SimulationMultivariateTimelineRequest.ParameterRamp("hemoglobinGdL",12.0)),60,15));
  assertEquals(5,r.points().size());
  assertEquals(4.0,r.points().get(0).parameterValues().get("pumpFlowLMin"),.001);
  assertEquals(6.0,r.points().get(4).parameterValues().get("pumpFlowLMin"),.001);
  assertEquals(10.0,r.points().get(0).parameterValues().get("hemoglobinGdL"),.001);
  assertEquals(12.0,r.points().get(4).parameterValues().get("hemoglobinGdL"),.001);
  assertTrue(r.points().get(4).estimatedOxygenDeliveryMlMin()>r.points().get(0).estimatedOxygenDeliveryMlMin());
 }
 @Test void rejectsDuplicateParameters(){
  assertThrows(ResponseStatusException.class,()->model.evaluate(new SimulationMultivariateTimelineRequest(baseline,List.of(
   new SimulationMultivariateTimelineRequest.ParameterRamp("pumpFlowLMin",5.0),
   new SimulationMultivariateTimelineRequest.ParameterRamp("pumpFlowLMin",6.0)),60,10)));
 }
 @Test void rejectsOutOfRangeTargets(){
  assertThrows(ResponseStatusException.class,()->model.evaluate(new SimulationMultivariateTimelineRequest(baseline,List.of(
   new SimulationMultivariateTimelineRequest.ParameterRamp("pumpFlowLMin",11.0)),60,10)));
 }
 @Test void rejectsTooManyPoints(){
  assertThrows(ResponseStatusException.class,()->model.evaluate(new SimulationMultivariateTimelineRequest(baseline,List.of(
   new SimulationMultivariateTimelineRequest.ParameterRamp("pumpFlowLMin",5.0)),3600,1)));
 }
}