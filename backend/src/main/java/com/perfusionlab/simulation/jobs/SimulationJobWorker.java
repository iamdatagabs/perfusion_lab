package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "worker")
public class SimulationJobWorker {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final SimplifiedPerfusionModel model;
    private final SimulationJobStore store;
    private final WorkerNodeStore nodes;
    private final String workerId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();
    private final ExecutionProfile profile;
    private final String hostname;
    private final int cpuCores;
    private final int memoryMb;
    private final int slots;

    public SimulationJobWorker(
            SimplifiedPerfusionModel model,
            SimulationJobStore store,
            WorkerNodeStore nodes,
            @Value("${perfusion.worker.profile:GENERAL}") String profile,
            @Value("${perfusion.worker.cpu-cores:1}") int cpuCores,
            @Value("${perfusion.worker.memory-mb:2048}") int memoryMb,
            @Value("${perfusion.worker.slots:1}") int slots) {
        this.model = model;
        this.store = store;
        this.nodes = nodes;
        this.profile = ExecutionProfile.valueOf(profile.toUpperCase());
        this.cpuCores = Math.max(1, cpuCores);
        this.memoryMb = Math.max(256, memoryMb);
        this.slots = Math.max(1, slots);
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            host = "unknown";
        }
        this.hostname = host;
    }

    @RabbitListener(queues = "#{@workerQueueName}")
    public void execute(SimulationJobMessage message) {
        if (message.executionProfile() != profile) {
            throw new IllegalArgumentException("Message routed to worker with incompatible execution profile");
        }
        if (!store.markRunning(message.jobId(), workerId, LEASE)) {
            return;
        }
        try {
            store.markSucceeded(message.jobId(), workerId, model.evaluate(message.input()));
        } catch (Exception exception) {
            store.markFailed(message.jobId(), workerId, exception);
        }
    }

    @Scheduled(fixedDelayString = "${perfusion.worker.heartbeat-ms:30000}")
    public void heartbeat() {
        store.heartbeat(workerId, LEASE);
        nodes.heartbeat(workerId, profile, hostname, cpuCores, memoryMb, slots, startedAt);
    }
}