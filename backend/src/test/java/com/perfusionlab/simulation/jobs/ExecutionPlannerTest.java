package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.perfusionlab.simulation.api.ProfileCapacity;
import com.perfusionlab.simulation.api.RequestedExecutionProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPlannerTest {
    private final WorkerNodeStore nodes = mock(WorkerNodeStore.class);
    private final ExecutionPlanner planner = new ExecutionPlanner(nodes);

    @Test
    void automaticModeChoosesPoolWithMostAvailableSlotsAndHealthyTelemetry() {
        when(nodes.profileCapacities()).thenReturn(List.of(
                new ProfileCapacity(ExecutionProfile.MEMORY, 1, 4, 1, 2, 8192, 0.92, 0.88),
                new ProfileCapacity(ExecutionProfile.COMPUTE, 2, 4, 3, 8, 8192, 0.25, 0.40)));

        SchedulingDecision decision = planner.plan(RequestedExecutionProfile.AUTO);

        assertEquals(ExecutionProfile.COMPUTE, decision.profile());
        assertEquals("AUTO_SELECTED_COMPUTE_AVAILABLE_SLOTS_3", decision.reason());
    }

    @Test
    void automaticModeFallsBackToGeneralWhenNoLiveFreeSlotsExist() {
        when(nodes.profileCapacities()).thenReturn(List.of(
                new ProfileCapacity(ExecutionProfile.MEMORY, 1, 1, 0, 2, 8192, 0.7, 0.6)));

        SchedulingDecision decision = planner.plan(RequestedExecutionProfile.AUTO);

        assertEquals(ExecutionProfile.GENERAL, decision.profile());
        assertEquals("AUTO_FALLBACK_GENERAL_NO_LIVE_FREE_SLOTS", decision.reason());
    }

    @Test
    void explicitProfileIsNotOverriddenByPlanner() {
        SchedulingDecision decision = planner.plan(RequestedExecutionProfile.MEMORY);

        assertEquals(ExecutionProfile.MEMORY, decision.profile());
        assertEquals("EXPLICIT_MEMORY", decision.reason());
        verifyNoInteractions(nodes);
    }
}
