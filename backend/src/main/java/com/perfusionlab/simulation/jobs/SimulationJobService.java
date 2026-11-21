package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import com.perfusionlab.simulation.observability.TraceContextSupport;
import org.slf4j.MDC;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class SimulationJobService {
    public static final String EXCHANGE = "perfusionlab.simulation";
    public static final String ROUTING_KEY = "simulation.execute.general";
    private final SimulationJobStore store;
    private final OutboxEventStore outbox;
    private final WorkerNodeStore nodes;
    private final TraceContextSupport traceContext;

    public SimulationJobService(SimulationJobStore store, OutboxEventStore outbox, WorkerNodeStore nodes,
                                TraceContextSupport traceContext) {
        this.store = store;
        this.outbox = outbox;
        this.nodes = nodes;
        this.traceContext = traceContext;
    }

    @Transactional
    public SimulationJobStatus submit(SimulationPreviewRequest input) {
        return submit(input, ExecutionProfile.GENERAL);
    }

    @Transactional
    public SimulationJobStatus submit(SimulationPreviewRequest input, ExecutionProfile profile) {
        UUID id = UUID.randomUUID();
        WorkerNodeAssignment assignment = nodes.reserveNode(profile).orElse(null);
        SimulationJobMessage message = traceContext.capture(new SimulationJobMessage(id, input, profile,
                assignment == null ? null : assignment.nodeId(),
                assignment == null ? null : assignment.incarnationId()), MDC.get("correlationId"));
        SimulationJobStatus job = store.create(id, input, null, profile, assignment,
                message.correlationId(), message.traceparent());
        outbox.enqueue(id, message);
        return job;
    }
}
