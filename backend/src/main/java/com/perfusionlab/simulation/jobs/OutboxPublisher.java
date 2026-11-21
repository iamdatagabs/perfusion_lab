package com.perfusionlab.simulation.jobs;

import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessagePostProcessor;
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
    private final MeterRegistry meters;

    public OutboxPublisher(OutboxEventStore outbox, RabbitTemplate rabbit, MeterRegistry meters) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.meters = meters;
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
                        message -> {
                            message.getMessageProperties().setHeader("X-Correlation-ID", event.message().correlationId());
                            if (event.message().traceparent() != null) {
                                message.getMessageProperties().setHeader("traceparent", event.message().traceparent());
                            }
                            return message;
                        },
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ rejected publication: " + confirm.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ returned an unroutable message");
                }
                outbox.markPublished(event.eventId());
                meters.counter("perfusionlab.outbox.events", "outcome", "published").increment();
            } catch (Exception exception) {
                outbox.markRetry(event.eventId(), event.attempts(), exception.getClass().getSimpleName());
                meters.counter("perfusionlab.outbox.events", "outcome", event.attempts() + 1 >= 8 ? "dead" : "retry").increment();
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
