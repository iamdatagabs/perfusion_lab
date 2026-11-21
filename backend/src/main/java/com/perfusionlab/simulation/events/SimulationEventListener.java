package com.perfusionlab.simulation.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SimulationEventListener {
    private static final Logger log = LoggerFactory.getLogger(SimulationEventListener.class);

    @EventListener
    public void onPreviewGenerated(SimulationPreviewGenerated event) {
        log.info("simulation.preview.generated modelVersion={} pumpFlowLMin={} oxygenDeliveryMlMin={}",
                event.modelVersion(), event.pumpFlowLMin(), event.estimatedOxygenDeliveryMlMin());
    }
}