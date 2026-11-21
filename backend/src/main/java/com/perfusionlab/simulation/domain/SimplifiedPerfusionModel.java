package com.perfusionlab.simulation.domain;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import com.perfusionlab.simulation.api.SimulationPreviewResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Simplified educational model. Not clinically validated and not for patient care.
 * Units: flow L/min, Hb g/dL, saturation fraction, SVR dyn*s/cm^5.
 */
@Component
public class SimplifiedPerfusionModel {
    public static final String MODEL_VERSION = "0.1.0-educational";

    public SimulationPreviewResponse evaluate(SimulationPreviewRequest input) {
        // Approximate MAP relation: MAP ~= CO * SVR / 80.
        double estimatedMap = input.pumpFlowLMin() * input.systemicVascularResistance() / 80.0;

        // DO2 (mL/min) ~= flow(L/min) * 10(dL/L) * Hb(g/dL) * 1.34 * SaO2.
        double oxygenDelivery = input.pumpFlowLMin() * 10.0
                * input.hemoglobinGdL() * 1.34 * input.arterialSaturation();
        double oxygenDeliveryIndex = oxygenDelivery / input.bodySurfaceAreaM2();

        return new SimulationPreviewResponse(
                MODEL_VERSION,
                Instant.now(),
                round(estimatedMap),
                round(oxygenDelivery),
                round(oxygenDeliveryIndex),
                input.pumpFlowLMin(),
                input.temperatureC(),
                "Educational calculation only. No clinical interpretation or treatment recommendation."
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}