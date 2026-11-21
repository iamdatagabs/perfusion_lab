package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class SimulationMessagingConfigurationTest {
    private final SimulationMessagingConfiguration configuration = new SimulationMessagingConfiguration();

    @Test
    void profileQueuesDeadLetterRejectedMessages() {
        Queue queue = configuration.generalSimulationQueue();

        assertEquals(SimulationMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(SimulationMessagingConfiguration.DEAD_LETTER_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void deadLetterQueueIsDurableAndHasDedicatedBinding() {
        Queue queue = configuration.simulationDeadLetterQueue();

        assertTrue(queue.isDurable());
        assertEquals(SimulationMessagingConfiguration.DEAD_LETTER_QUEUE, queue.getName());
        assertEquals(SimulationMessagingConfiguration.DEAD_LETTER_ROUTING_KEY,
                configuration.simulationDeadLetterBinding(queue,
                        configuration.simulationDeadLetterExchange()).getRoutingKey());
    }

    @Test
    void everyExecutionProfileQueueUsesDeadLetterPolicy() {
        for (Queue queue : new Queue[]{configuration.generalSimulationQueue(),
                configuration.memorySimulationQueue(), configuration.computeSimulationQueue()}) {
            assertEquals(SimulationMessagingConfiguration.DEAD_LETTER_EXCHANGE,
                    queue.getArguments().get("x-dead-letter-exchange"));
        }
    }
}
