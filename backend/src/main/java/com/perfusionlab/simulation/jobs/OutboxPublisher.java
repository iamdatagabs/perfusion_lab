package com.perfusionlab.simulation.jobs;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class OutboxPublisher {
    private static final String NODE_ROUTING_PREFIX = "simulation.execute.node.";
    private final OutboxEventStore outbox;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(OutboxEventStore outbox, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${perfusion.outbox.poll-ms:500}")
    public void publishPending() {
        for (int i = 0; i < 50; i++) {
            var candidate = outbox.claimNext();
            if (candidate.isEmpty()) return;
            var event = candidate.get();
            try {
                CorrelationData correlation = new CorrelationData(event.eventId().toString());
                rabbit.convertAndSend(
                        SimulationJobService.EXCHANGE,
                        routingKey(event.message()),
                        event.message(),
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ rejected publication: " + confirm.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ returned an unroutable message");
                }
                outbox.markPublished(event.eventId());
            } catch (Exception exception) {
                outbox.markRetry(event.eventId(), event.attempts(), exception.getClass().getSimpleName());
            }
        }
    }

    private String routingKey(SimulationJobMessage message) {
        if (message.targetNodeId() == null || message.targetNodeId().isBlank()) {
            return message.executionProfile().routingKey();
        }
        if (!message.targetNodeId().matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid target node identifier");
        }
        return NODE_ROUTING_PREFIX + message.targetNodeId();
    }
}
