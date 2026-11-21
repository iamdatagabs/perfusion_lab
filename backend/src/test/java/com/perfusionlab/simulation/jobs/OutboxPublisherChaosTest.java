package com.perfusionlab.simulation.jobs;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import com.perfusionlab.simulation.observability.TraceContextSupport;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherChaosTest {
    @Test
    void brokerFailureSchedulesOutboxRetryInsteadOfLosingEvent() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        TraceContextSupport traceContext = mock(TraceContextSupport.class);
        when(traceContext.capture(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(traceContext.inSpan(any(), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        UUID eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var input = new SimulationPreviewRequest(4.8, 10.0, 0.98, 1200.0, 1.9, 35.5);
        var event = new OutboxEventStore.OutboxEvent(eventId, jobId,
                new SimulationJobMessage(jobId, input), 0);
        when(outbox.claimNext()).thenReturn(Optional.of(event), Optional.empty());
        doThrow(new IllegalStateException("simulated broker outage"))
                .when(rabbit).convertAndSend(eq(SimulationJobService.EXCHANGE), anyString(),
                        any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(outbox, rabbit, meters, traceContext).publishPending();

        verify(outbox).markRetry(eventId, 0, "IllegalStateException");
        assertEquals(1.0, meters.counter("perfusionlab.outbox.events", "outcome", "retry").count());
    }

    @Test
    void emptyOutboxDoesNotPublishAnything() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(outbox.claimNext()).thenReturn(Optional.empty());

        new OutboxPublisher(outbox, rabbit, new SimpleMeterRegistry(), mock(TraceContextSupport.class)).publishPending();

        verifyNoInteractions(rabbit);
    }
}
