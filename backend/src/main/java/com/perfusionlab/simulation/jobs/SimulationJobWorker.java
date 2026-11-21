package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.domain.SimplifiedPerfusionModel;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.perfusionlab.simulation.observability.TraceContextSupport;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "worker")
public class SimulationJobWorker {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String NODE_ROUTING_PREFIX = "simulation.execute.node.";
    private static final String NODE_QUEUE_PREFIX = "perfusionlab.simulation.node.";
    private final SimplifiedPerfusionModel model;
    private final SimulationJobStore store;
    private final WorkerNodeStore nodes;
    private final WorkerTelemetryCollector telemetryCollector;
    private final RabbitAdmin rabbitAdmin;
    private final RabbitListenerEndpointRegistry listenerRegistry;
    private final MeterRegistry meters;
    private final TraceContextSupport traceContext;
    private final String workerId;
    private final String nodeId;
    private final UUID incarnationId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private final ExecutionProfile profile;
    private final String hostname;
    private final int cpuCores, memoryMb, slots;

    public SimulationJobWorker(SimplifiedPerfusionModel model, SimulationJobStore store,
            WorkerNodeStore nodes, WorkerTelemetryCollector telemetryCollector,
            RabbitAdmin rabbitAdmin, RabbitListenerEndpointRegistry listenerRegistry, MeterRegistry meters, TraceContextSupport traceContext,
            @Value("${perfusion.worker.profile:GENERAL}") String profile,
            @Value("${perfusion.worker.node-id:}") String configuredNodeId,
            @Value("${perfusion.worker.cpu-cores:1}") int cpuCores,
            @Value("${perfusion.worker.memory-mb:2048}") int memoryMb,
            @Value("${perfusion.worker.slots:1}") int slots) {
        this.model = model; this.store = store; this.nodes = nodes;
        this.telemetryCollector = telemetryCollector; this.rabbitAdmin = rabbitAdmin;
        this.listenerRegistry = listenerRegistry; this.meters = meters; this.traceContext = traceContext;
        this.profile = ExecutionProfile.valueOf(profile.toUpperCase());
        this.cpuCores = Math.max(1, cpuCores);
        this.memoryMb = Math.max(256, memoryMb);
        this.slots = Math.max(1, slots);
        String host;
        try { host = InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { host = "unknown"; }
        this.hostname = host;
        String resolved = configuredNodeId == null || configuredNodeId.isBlank()
                ? host + "-" + this.profile.name().toLowerCase() : configuredNodeId.trim();
        if (!resolved.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("WORKER_NODE_ID must contain only letters, digits, '_' or '-' and be at most 80 characters");
        this.nodeId = resolved;
        // The incarnation is part of workerId so a restarted process cannot reuse an old lease owner.
        this.workerId = nodeId + ":" + incarnationId;
    }

    public String getNodeQueueName() {
        return NODE_QUEUE_PREFIX + nodeId;
    }

    public String getNodeRoutingKey() {
        return NODE_ROUTING_PREFIX + nodeId;
    }

    @PostConstruct
    public void registerNode() {
        // Provision the node-specific durable queue before advertising this node as READY.
        rabbitAdmin.declareExchange(new DirectExchange(SimulationJobService.EXCHANGE, true, false));
        rabbitAdmin.declareQueue(QueueBuilder.durable(getNodeQueueName())
                .deadLetterExchange(SimulationMessagingConfiguration.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(SimulationMessagingConfiguration.DEAD_LETTER_ROUTING_KEY).build());
        rabbitAdmin.declareBinding(new Binding(getNodeQueueName(), DestinationType.QUEUE,
                SimulationJobService.EXCHANGE, getNodeRoutingKey(), null));
        publishHeartbeat();
    }

    @RabbitListener(id = "workerListener", queues = {"#{@workerQueueName}", "#{@simulationJobWorker.nodeQueueName}"})
    public void execute(SimulationJobMessage message) {
        traceContext.inWorkerSpan(message, () -> {
            executeInContext(message);
            return null;
        });
    }

    void executeInContext(SimulationJobMessage message) {
        meters.counter("perfusionlab.worker.messages", "profile", profile.name(), "outcome", "received").increment();
        Timer.Sample sample = Timer.start(meters);
        if (message.executionProfile() != profile) {
            meters.counter("perfusionlab.worker.messages", "profile", profile.name(), "outcome", "rejected").increment();
            throw new IllegalArgumentException("Message routed to worker with incompatible execution profile");
        }
        // A durable node queue can outlive a process incarnation. Acknowledge
        // obsolete or misdirected target messages instead of requeueing poison messages.
        if (message.targetNodeId() != null && !message.targetNodeId().equals(nodeId)) return;
        if (message.targetIncarnationId() != null && !message.targetIncarnationId().equals(incarnationId)) return;
        if (!store.markRunning(message.jobId(), workerId, LEASE)) {
            // Profile queues are shared by all nodes. If this node has entered
            // DRAINING, return untargeted work to the broker for another READY
            // consumer instead of acknowledging and losing it.
            if (message.targetNodeId() == null
                    && !"READY".equals(nodes.nodeStatus(nodeId, incarnationId))) {
                throw new ImmediateRequeueAmqpException("Worker node is draining; return shared work to the queue");
            }
            return;
        }
        try {
            store.markSucceeded(message.jobId(), workerId, model.evaluate(message.input()));
            meters.counter("perfusionlab.worker.messages", "profile", profile.name(), "outcome", "succeeded").increment();
        } catch (Exception exception) {
            store.markFailed(message.jobId(), workerId, exception);
            meters.counter("perfusionlab.worker.messages", "profile", profile.name(), "outcome", "failed").increment();
        } finally {
            sample.stop(Timer.builder("perfusionlab.worker.execution")
                    .tag("profile", profile.name()).register(meters));
        }
    }

    @Scheduled(fixedDelayString = "${perfusion.worker.heartbeat-ms:30000}")
    public void heartbeat() {
        store.heartbeat(workerId, LEASE);
        publishHeartbeat();
        if (!"READY".equals(nodes.nodeStatus(nodeId, incarnationId))) {
            var container = listenerRegistry.getListenerContainer("workerListener");
            if (container != null && container.isRunning()) container.stop();
        }
    }

    private void publishHeartbeat() {
        nodes.heartbeat(workerId, nodeId, incarnationId, profile, hostname,
                cpuCores, memoryMb, slots, startedAt, telemetryCollector.collect());
    }
}
