package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.pde.OxygenTransportPdeModel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/simulations")
@CrossOrigin(origins = "*")
public class PdeTransportController {
    private final OxygenTransportPdeModel model;

    public PdeTransportController(OxygenTransportPdeModel model) {
        this.model = model;
    }

    @PostMapping("/pde-transport-preview")
    public PdeTransportResponse preview(@Valid @RequestBody PdeTransportRequest request) {
        return model.evaluate(request);
    }
}
