package com.perfusionlab.simulation.jobs;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SimulationJobStore {
    private final Map<UUID, SimulationJobStatus> jobs = new ConcurrentHashMap<>();

    public SimulationJobStatus create(UUID id) {
        var job = new SimulationJobStatus(id, SimulationJobStatus.State.QUEUED,
                Instant.now(), null, null, null, null);
        jobs.put(id, job);
        return job;
    }

    public Optional<SimulationJobStatus> find(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public void markRunning(UUID id) {
        update(id, new SimulationJobStatus(id, SimulationJobStatus.State.RUNNING,
                submittedAt(id), Instant.now(), null, null, null));
    }

    public void markSucceeded(UUID id, com.perfusionlab.simulation.api.SimulationPreviewResponse result) {
        update(id, new SimulationJobStatus(id, SimulationJobStatus.State.SUCCEEDED,
                submittedAt(id), startedAt(id), Instant.now(), result, null));
    }

    public void markFailed(UUID id, Exception error) {
        update(id, new SimulationJobStatus(id, SimulationJobStatus.State.FAILED,
                submittedAt(id), startedAt(id), Instant.now(), null,
                error.getClass().getSimpleName()));
    }

    private Instant submittedAt(UUID id) {
        var current = jobs.get(id);
        return current == null ? Instant.now() : current.submittedAt();
    }

    private Instant startedAt(UUID id) {
        var current = jobs.get(id);
        return current == null ? null : current.startedAt();
    }

    private void update(UUID id, SimulationJobStatus status) {
        jobs.computeIfPresent(id, (key, current) -> status);
    }
}
