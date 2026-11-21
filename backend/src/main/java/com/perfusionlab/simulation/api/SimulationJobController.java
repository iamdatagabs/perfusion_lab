package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.SimulationJobService;
import com.perfusionlab.simulation.jobs.SimulationJobStatus;
import com.perfusionlab.simulation.jobs.SimulationJobStore;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations/jobs")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class SimulationJobController {
    private final SimulationJobService service;
    private final SimulationJobStore store;

    public SimulationJobController(SimulationJobService service, SimulationJobStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<SimulationJobStatus> submit(@Valid @RequestBody SimulationPreviewRequest request) {
        SimulationJobStatus job = service.submit(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/simulations/jobs/" + job.jobId()))
                .body(job);
    }

    @GetMapping
    public List<SimulationJobStatus> recent(@RequestParam(defaultValue = "50") int limit) {
        return store.findRecent(limit);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<SimulationJobStatus> status(@PathVariable UUID jobId) {
        return store.find(jobId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
