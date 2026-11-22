package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.domain.CoupledOdePdePerfusionModel;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/simulations")
@CrossOrigin(origins = "*")
public class CoupledSimulationController {
    private final CoupledOdePdePerfusionModel model;

    public CoupledSimulationController(CoupledOdePdePerfusionModel model) {
        this.model = model;
    }

    @PostMapping(value = "/coupled-preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CoupledSimulationResponse preview(@Valid @RequestBody CoupledSimulationRequest request) {
        return model.evaluate(request);
    }
}
