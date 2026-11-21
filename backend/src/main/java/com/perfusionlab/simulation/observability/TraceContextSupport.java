package com.perfusionlab.simulation.observability;

import com.perfusionlab.simulation.jobs.SimulationJobMessage;
import io.micrometer.tracing.Propagator;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TraceContextSupport {
    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextSupport(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public SimulationJobMessage capture(SimulationJobMessage message, String correlationId) {
        Map<String, String> carrier = new HashMap<>();
        Span current = tracer.currentSpan();
        if (current != null) {
            propagator.inject(current.context(), carrier, Map::put);
        }
        return message.withContext(
                correlationId == null || correlationId.isBlank() ? message.correlationId() : correlationId,
                carrier.get("traceparent"));
    }

    public <T> T inSpan(SimulationJobMessage message, String spanName, Supplier<T> work) {
        Span.Builder parent = propagator.extract(message,
                (carrier, key) -> "traceparent".equalsIgnoreCase(key) ? carrier.traceparent() : null);
        Span span = parent.name(spanName)
                .tag("correlation.id", message.correlationId())
                .tag("simulation.job_id", message.jobId().toString())
                .start();
        String previousCorrelation = MDC.get("correlationId");
        MDC.put("correlationId", message.correlationId());
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return work.get();
        } finally {
            span.end();
            if (previousCorrelation == null) MDC.remove("correlationId");
            else MDC.put("correlationId", previousCorrelation);
        }
    }

    public <T> T inWorkerSpan(SimulationJobMessage message, Supplier<T> work) {
        return inSpan(message, "perfusionlab.simulation.execute", work);
    }
}
