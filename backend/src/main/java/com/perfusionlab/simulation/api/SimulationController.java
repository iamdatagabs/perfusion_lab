package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import com.perfusionlab.simulation.events.SimulationPreviewGenerated;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
@CrossOrigin(origins = "*")
public class SimulationController {
    private final SimplifiedPerfusionModel model;
    private final ApplicationEventPublisher events;

    public SimulationController(SimplifiedPerfusionModel model, ApplicationEventPublisher events) {
        this.model = model;
        this.events = events;
    }

    @PostMapping("/preview")
    public SimulationPreviewResponse preview(@Valid @RequestBody SimulationPreviewRequest request) {
        SimulationPreviewResponse result = model.evaluate(request);
        events.publishEvent(new SimulationPreviewGenerated(
                result.modelVersion(),
                Instant.now(),
                result.pumpFlowLMin(),
                result.estimatedOxygenDeliveryMlMin()
        ));
        return result;
    }
}