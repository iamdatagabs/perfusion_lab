package com.perfusionlab.simulation.api;
import com.perfusionlab.simulation.domain.EducationalTimelineModel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/simulations")
@CrossOrigin(origins="*")
public class SimulationTimelineController {
 private final EducationalTimelineModel timelineModel;
 public SimulationTimelineController(EducationalTimelineModel timelineModel){this.timelineModel=timelineModel;}
 @PostMapping("/timeline")
 public SimulationTimelineResponse timeline(@Valid @RequestBody SimulationTimelineRequest request){return timelineModel.evaluate(request);}
}