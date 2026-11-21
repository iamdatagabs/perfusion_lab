package com.perfusionlab.simulation.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class DeadLetterQueueMetrics {
    private final RabbitAdmin rabbitAdmin;

    public DeadLetterQueueMetrics(RabbitAdmin rabbitAdmin, MeterRegistry meters) {
        this.rabbitAdmin = rabbitAdmin;
        Gauge.builder("perfusionlab.rabbitmq.dead_letter.messages", this, DeadLetterQueueMetrics::messageCount)
                .description("Messages currently waiting in the simulation dead-letter queue")
                .register(meters);
    }

    private double messageCount() {
        try {
            var info = rabbitAdmin.getQueueInfo(SimulationMessagingConfiguration.DEAD_LETTER_QUEUE);
            return info == null ? 0 : info.getMessageCount();
        } catch (Exception unavailable) {
            // Keep the gauge observable without making an unavailable broker crash the API.
            return Double.NaN;
        }
    }
}
