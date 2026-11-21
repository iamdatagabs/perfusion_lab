package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.ExperimentBatchRequest;
import com.perfusionlab.simulation.api.ExperimentBatchStatus;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class ExperimentBatchService {
    private final ExperimentBatchStore batches;
    private final SimulationJobStore jobs;
    private final OutboxEventStore outbox;
    private final ExecutionPlanner planner;

    public ExperimentBatchService(ExperimentBatchStore batches, SimulationJobStore jobs,
                                  OutboxEventStore outbox, ExecutionPlanner planner) {
        this.batches = batches;
        this.jobs = jobs;
        this.outbox = outbox;
        this.planner = planner;
    }

    @Transactional
    public ExperimentBatchStatus submit(ExperimentBatchRequest request) {
        SchedulingDecision decision = planner.plan(request.executionProfile());
        UUID batchId = UUID.randomUUID();
        batches.create(batchId, decision.profile(), request.executionProfile(),
                decision.reason(), request.parallelism(), request.simulations().size(),
                request.baselineSequentialMs());
        request.simulations().forEach(input -> {
            UUID jobId = UUID.randomUUID();
            jobs.create(jobId, input, batchId, decision.profile());
            outbox.enqueue(batchId, new SimulationJobMessage(jobId, input, decision.profile()));
        });
        return batches.find(batchId).orElseThrow();
    }

    public java.util.Optional<ExperimentBatchStatus> find(UUID id) {
        return batches.find(id);
    }
}
