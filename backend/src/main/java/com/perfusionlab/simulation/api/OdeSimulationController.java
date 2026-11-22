package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.domain.RungeKuttaOdePerfusionModel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/simulations")
@CrossOrigin(origins = "*")
public class OdeSimulationController {
    private final RungeKuttaOdePerfusionModel model;

    public OdeSimulationController(RungeKuttaOdePerfusionModel model) {
        this.model = model;
    }

    @PostMapping("/ode-preview")
    public OdeSimulationResponse preview(@Valid @RequestBody OdeSimulationRequest request) {
        return model.evaluate(request);
    }
}
