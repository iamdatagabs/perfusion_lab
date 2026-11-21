package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.WorkerNodeDrainService;
import com.perfusionlab.simulation.jobs.WorkerNodeDrainSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cluster/nodes")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class WorkerNodeOperationsController {
    private final WorkerNodeDrainService drainService;
    private final String adminToken;

    public WorkerNodeOperationsController(WorkerNodeDrainService drainService,
            @Value("\u0024{perfusion.admin.token:}") String adminToken) {
        this.drainService = drainService;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    @PostMapping("/{nodeId}/drain")
    public ResponseEntity<?> drain(@PathVariable String nodeId,
            @RequestBody DrainWorkerNodeRequest request,
            @RequestHeader(name = "X-Perfusion-Admin-Token", required = false) String suppliedToken) {
        if (adminToken.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Node operations are disabled: PERFUSION_ADMIN_TOKEN is not configured");
        if (suppliedToken == null || !MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            WorkerNodeDrainSnapshot result = drainService.drain(nodeId, request.incarnationId());
            return ResponseEntity.accepted().body(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        }
    }

    public record DrainWorkerNodeRequest(UUID incarnationId) {}
}
