package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import java.time.Duration;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "worker")
public class SimulationJobWorker {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final SimplifiedPerfusionModel model;
    private final SimulationJobStore store;
    private final String workerId = UUID.randomUUID().toString();

    public SimulationJobWorker(SimplifiedPerfusionModel model, SimulationJobStore store) {
        this.model = model;
        this.store = store;
    }

    @RabbitListener(queues = SimulationMessagingConfiguration.QUEUE)
    public void execute(SimulationJobMessage message) {
        if (!store.markRunning(message.jobId(), workerId, LEASE)) {
            return;
        }
        try {
            store.markSucceeded(message.jobId(), model.evaluate(message.input()));
        } catch (Exception exception) {
            store.markFailed(message.jobId(), exception);
        }
    }

    @Scheduled(fixedDelayString = "${perfusion.worker.heartbeat-ms:30000}")
    public void heartbeat() {
        store.heartbeat(workerId, LEASE);
    }
}