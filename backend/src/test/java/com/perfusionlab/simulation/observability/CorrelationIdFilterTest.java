package com.perfusionlab.simulation.observability;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeIncomingCorrelationIdAndReturnsIt() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "experiment-42:attempt-2");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("experiment-42:attempt-2", response.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test
    void replacesInvalidCorrelationIdWithGeneratedUuid() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "bad value\r\nInjected: yes");

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(CorrelationIdFilter.HEADER));
        assertDoesNotThrow(() -> java.util.UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
    }
}
