package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.pde.CoupledConcentrationFieldModel;
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
public class CoupledFieldController {
    private final CoupledConcentrationFieldModel model;

    public CoupledFieldController(CoupledConcentrationFieldModel model) {
        this.model = model;
    }

    @PostMapping(value = "/coupled-field-preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CoupledFieldResponse preview(@Valid @RequestBody CoupledFieldRequest request) {
        return model.evaluate(request);
    }
}
