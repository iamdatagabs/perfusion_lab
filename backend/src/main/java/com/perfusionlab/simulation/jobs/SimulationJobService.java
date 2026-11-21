package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class SimulationJobService {
    public static final String EXCHANGE = "perfusionlab.simulation";
    public static final String ROUTING_KEY = "simulation.execute";
    private final SimulationJobStore store;
    private final OutboxEventStore outbox;

    public SimulationJobService(SimulationJobStore store, OutboxEventStore outbox) {
        this.store = store;
        this.outbox = outbox;
    }

    @Transactional
    public SimulationJobStatus submit(SimulationPreviewRequest input) {
        UUID id = UUID.randomUUID();
        SimulationJobStatus job = store.create(id, input);
        outbox.enqueue(id, new SimulationJobMessage(id, input));
        return job;
    }
}