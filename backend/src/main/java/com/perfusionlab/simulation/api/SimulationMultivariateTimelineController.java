package com.perfusionlab.simulation.api;
import com.perfusionlab.simulation.domain.EducationalMultivariateTimelineModel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/simulations")
@CrossOrigin(origins="*")
public class SimulationMultivariateTimelineController {
 private final EducationalMultivariateTimelineModel model;
 public SimulationMultivariateTimelineController(EducationalMultivariateTimelineModel model){this.model=model;}
 @PostMapping("/timeline/multivariate")
 public SimulationMultivariateTimelineResponse timeline(@Valid @RequestBody SimulationMultivariateTimelineRequest request){
  return model.evaluate(request);
 }
}