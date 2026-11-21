package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "worker")
public class SimulationJobWorker {
    private final SimplifiedPerfusionModel model;
    private final SimulationJobStore store;

    public SimulationJobWorker(SimplifiedPerfusionModel model, SimulationJobStore store) {
        this.model = model;
        this.store = store;
    }

    @RabbitListener(queues = SimulationMessagingConfiguration.QUEUE)
    public void execute(SimulationJobMessage message) {
        // Compare-and-set prevents duplicate deliveries being executed concurrently.
        if (!store.markRunning(message.jobId())) {
            return;
        }
        try {
            store.markSucceeded(message.jobId(), model.evaluate(message.input()));
        } catch (Exception exception) {
            store.markFailed(message.jobId(), exception);
        }
    }
}
