package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SimulationJobWorker {
    private final SimplifiedPerfusionModel model;
    private final SimulationJobStore store;

    public SimulationJobWorker(SimplifiedPerfusionModel model, SimulationJobStore store) {
        this.model = model;
        this.store = store;
    }

    @RabbitListener(queues = SimulationMessagingConfiguration.QUEUE)
    public void execute(SimulationJobMessage message) {
        store.markRunning(message.jobId());
        try {
            store.markSucceeded(message.jobId(), model.evaluate(message.input()));
        } catch (Exception exception) {
            // Record the failure and acknowledge this message; retries/DLQ policy
            // will be introduced with persistent job state and idempotency.
            store.markFailed(message.jobId(), exception);
        }
    }
}
