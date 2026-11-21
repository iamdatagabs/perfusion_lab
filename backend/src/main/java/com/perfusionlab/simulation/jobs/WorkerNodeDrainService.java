package com.perfusionlab.simulation.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class WorkerNodeDrainService {
    private final WorkerNodeStore nodes;

    public WorkerNodeDrainService(WorkerNodeStore nodes) { this.nodes = nodes; }

    @Scheduled(fixedDelayString = "${perfusion.worker.drain-reconcile-ms:5000}")
    @Transactional
    public void completeIdleDrains() {
        nodes.completeIdleDrains();
    }

    @Transactional
    public WorkerNodeDrainSnapshot drain(String nodeId, java.util.UUID incarnationId) {
        String current = nodes.nodeStatus(nodeId, incarnationId);
        if (current == null) throw new IllegalArgumentException("Worker node incarnation not found");
        if ("READY".equals(current)) {
            nodes.markDraining(nodeId, incarnationId);
        } else if (!"DRAINING".equals(current) && !"OFFLINE".equals(current)) {
            throw new IllegalStateException("Worker node cannot be drained from status " + current);
        }
        int reassigned = "OFFLINE".equals(current) ? 0
                : nodes.requeueWaitingAssignments(nodeId, incarnationId);
        nodes.markOfflineIfIdle(nodeId, incarnationId);
        return nodes.drainSnapshot(nodeId, incarnationId, reassigned);
    }
}
