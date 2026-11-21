package com.perfusionlab.simulation.domain;
import com.perfusionlab.simulation.api.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
/** Multiple linearly interpolated inputs evaluated against the static educational model. */
@Component
public class EducationalMultivariateTimelineModel {
 private final SimplifiedPerfusionModel model;
 private final EducationalTimelineModel validator;
 public EducationalMultivariateTimelineModel(SimplifiedPerfusionModel model, EducationalTimelineModel validator){
  this.model=model; this.validator=validator;
 }
 public SimulationMultivariateTimelineResponse evaluate(SimulationMultivariateTimelineRequest request){
  Set<String> names=new LinkedHashSet<>();
  for(var ramp:request.ramps()){
   if(!names.add(ramp.parameter())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each parameter may appear only once.");
   validator.validateTarget(ramp.parameter(),ramp.endValue());
  }
  int duration=request.durationSeconds(),step=request.stepSeconds();
  int count=(int)Math.ceil(duration/(double)step)+1;
  if(count>361) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Timeline exceeds 361 points; increase stepSeconds.");
  List<SimulationMultivariateTimelineResponse.Point> points=new ArrayList<>(count+1);
  for(int elapsed=0;elapsed<=duration;elapsed+=step) points.add(point(request,elapsed,duration));
  if(points.get(points.size()-1).elapsedSeconds()!=duration) points.add(point(request,duration,duration));
  return new SimulationMultivariateTimelineResponse(SimplifiedPerfusionModel.MODEL_VERSION,Instant.now(),duration,step,
   List.copyOf(names),List.copyOf(points),
   "Educational multivariable scenario only: selected inputs are linearly interpolated and the static model is recalculated at each time point. No physiological dynamics, clinical validation, interpretation or treatment recommendation.");
 }
 private SimulationMultivariateTimelineResponse.Point point(SimulationMultivariateTimelineRequest r,int elapsed,int duration){
  double fraction=elapsed/(double)duration;
  SimulationPreviewRequest input=r.baseline();
  Map<String,Double> values=new LinkedHashMap<>();
  for(var ramp:r.ramps()){
   double start=valueOf(r.baseline(),ramp.parameter());
   double value=round(start+(ramp.endValue()-start)*fraction);
   values.put(ramp.parameter(),value);
   input=withValue(input,ramp.parameter(),value);
  }
  SimulationPreviewResponse result=model.evaluate(input);
  return new SimulationMultivariateTimelineResponse.Point(elapsed,Map.copyOf(values),result.estimatedMapMmHg(),
   result.estimatedOxygenDeliveryMlMin(),result.estimatedOxygenDeliveryIndexMlMinM2());
 }
 private double valueOf(SimulationPreviewRequest i,String p){return switch(p){
  case "pumpFlowLMin"->i.pumpFlowLMin();case "hemoglobinGdL"->i.hemoglobinGdL();
  case "arterialSaturation"->i.arterialSaturation();case "systemicVascularResistance"->i.systemicVascularResistance();
  case "bodySurfaceAreaM2"->i.bodySurfaceAreaM2();case "temperatureC"->i.temperatureC();
  default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported parameter.");};}
 private SimulationPreviewRequest withValue(SimulationPreviewRequest i,String p,double v){return switch(p){
  case "pumpFlowLMin"->new SimulationPreviewRequest(v,i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "hemoglobinGdL"->new SimulationPreviewRequest(i.pumpFlowLMin(),v,i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "arterialSaturation"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),v,i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "systemicVascularResistance"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),v,i.bodySurfaceAreaM2(),i.temperatureC());
  case "bodySurfaceAreaM2"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),v,i.temperatureC());
  case "temperatureC"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),v);
  default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported parameter.");};}
 private double round(double v){return Math.round(v*100.0)/100.0;}
}