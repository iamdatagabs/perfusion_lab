package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
@CrossOrigin(origins = "*")
public class SimulationController {
    private final SimplifiedPerfusionModel model;

    public SimulationController(SimplifiedPerfusionModel model) {
        this.model = model;
    }

    @PostMapping("/preview")
    public SimulationPreviewResponse preview(@Valid @RequestBody SimulationPreviewRequest request) {
        return model.evaluate(request);
    }
}