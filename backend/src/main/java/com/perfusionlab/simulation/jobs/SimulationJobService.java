package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimulationJobService {
    public static final String EXCHANGE = "perfusionlab.simulation";
    public static final String ROUTING_KEY = "simulation.execute";
    private final RabbitTemplate rabbitTemplate;
    private final SimulationJobStore store;

    public SimulationJobService(RabbitTemplate rabbitTemplate, SimulationJobStore store) {
        this.rabbitTemplate = rabbitTemplate;
        this.store = store;
    }

    public SimulationJobStatus submit(SimulationPreviewRequest input) {
        UUID id = UUID.randomUUID();
        SimulationJobStatus job = store.create(id);
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new SimulationJobMessage(id, input));
            return job;
        } catch (RuntimeException exception) {
            store.markFailed(id, exception);
            throw exception;
        }
    }
}
