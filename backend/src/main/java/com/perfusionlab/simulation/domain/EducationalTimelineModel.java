package com.perfusionlab.simulation.domain;
import com.perfusionlab.simulation.api.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
/** Time-indexed scenario: linearly changes one input and recalculates a static educational model.
 * This is not a dynamic physiological model. */
@Component
public class EducationalTimelineModel {
 private final SimplifiedPerfusionModel model;
 public EducationalTimelineModel(SimplifiedPerfusionModel model){this.model=model;}
 public SimulationTimelineResponse evaluate(SimulationTimelineRequest request){
  validateEndValue(request.parameter(),request.endValue());
  int duration=request.durationSeconds(), step=request.stepSeconds();
  int pointCount=(int)Math.ceil(duration/(double)step)+1;
  if(pointCount>361) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Timeline exceeds 361 points; increase stepSeconds.");
  List<SimulationTimelineResponse.SimulationTimelinePoint> points=new ArrayList<>(pointCount+1);
  for(int elapsed=0;elapsed<=duration;elapsed+=step) points.add(point(request,elapsed,duration));
  if(points.get(points.size()-1).elapsedSeconds()!=duration) points.add(point(request,duration,duration));
  return new SimulationTimelineResponse(SimplifiedPerfusionModel.MODEL_VERSION,Instant.now(),request.parameter(),
   duration,step,List.copyOf(points),"Educational scenario only: one input is linearly interpolated and the static model is recalculated at each time point. No physiological dynamics, clinical validation, interpretation or treatment recommendation.");
 }
 private SimulationTimelineResponse.SimulationTimelinePoint point(SimulationTimelineRequest r,int elapsed,int duration){
  double fraction=elapsed/(double)duration, start=valueOf(r.baseline(),r.parameter());
  double value=start+(r.endValue()-start)*fraction;
  SimulationPreviewResponse result=model.evaluate(withValue(r.baseline(),r.parameter(),value));
  return new SimulationTimelineResponse.SimulationTimelinePoint(elapsed,round(value),result.estimatedMapMmHg(),
   result.estimatedOxygenDeliveryMlMin(),result.estimatedOxygenDeliveryIndexMlMinM2());
 }
 private double valueOf(SimulationPreviewRequest i,String p){return switch(p){
  case "pumpFlowLMin"->i.pumpFlowLMin(); case "hemoglobinGdL"->i.hemoglobinGdL();
  case "arterialSaturation"->i.arterialSaturation(); case "systemicVascularResistance"->i.systemicVascularResistance();
  case "bodySurfaceAreaM2"->i.bodySurfaceAreaM2(); case "temperatureC"->i.temperatureC();
  default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported parameter.");};}
 private SimulationPreviewRequest withValue(SimulationPreviewRequest i,String p,double v){return switch(p){
  case "pumpFlowLMin"->new SimulationPreviewRequest(v,i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "hemoglobinGdL"->new SimulationPreviewRequest(i.pumpFlowLMin(),v,i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "arterialSaturation"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),v,i.systemicVascularResistance(),i.bodySurfaceAreaM2(),i.temperatureC());
  case "systemicVascularResistance"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),v,i.bodySurfaceAreaM2(),i.temperatureC());
  case "bodySurfaceAreaM2"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),v,i.temperatureC());
  case "temperatureC"->new SimulationPreviewRequest(i.pumpFlowLMin(),i.hemoglobinGdL(),i.arterialSaturation(),i.systemicVascularResistance(),i.bodySurfaceAreaM2(),v);
  default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported parameter.");};}
 public void validateTarget(String parameter,double value){ validateEndValue(parameter,value); }
 private void validateEndValue(String p,double v){
  double min,max; switch(p){
   case "pumpFlowLMin"->{min=.1;max=10;} case "hemoglobinGdL"->{min=1;max=25;}
   case "arterialSaturation"->{min=.5;max=1;} case "systemicVascularResistance"->{min=100;max=5000;}
   case "bodySurfaceAreaM2"->{min=.3;max=3;} case "temperatureC"->{min=15;max=40;}
   default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported parameter.");}
  if(!Double.isFinite(v)||v<min||v>max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"endValue outside supported range for "+p+".");
 }
 private double round(double v){return Math.round(v*100.0)/100.0;}
}