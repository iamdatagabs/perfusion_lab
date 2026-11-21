package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExperimentBatchService;
import com.perfusionlab.simulation.jobs.WorkerNodeStore;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class ExperimentBatchController {
    private final ExperimentBatchService batches;
    private final WorkerNodeStore nodes;

    public ExperimentBatchController(ExperimentBatchService batches, WorkerNodeStore nodes) {
        this.batches = batches;
        this.nodes = nodes;
    }

    @PostMapping("/experiments/batches")
    public ResponseEntity<ExperimentBatchStatus> submit(@Valid @RequestBody ExperimentBatchRequest request) {
        ExperimentBatchStatus batch = batches.submit(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/experiments/batches/" + batch.batchId()))
                .body(batch);
    }

    @GetMapping("/experiments/batches/{batchId}")
    public ResponseEntity<ExperimentBatchStatus> status(@PathVariable UUID batchId) {
        return batches.find(batchId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/cluster/nodes")
    public List<WorkerNodeStatus> nodes() {
        return nodes.activeNodes();
    }
}