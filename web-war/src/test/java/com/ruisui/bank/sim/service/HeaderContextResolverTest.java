package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderContextResolverTest {

    private final HeaderContextResolver resolver = new HeaderContextResolver();

    @Test
    void resolveDefaultsChannelToWebfeWhenHeaderMissing() {
        MockHttpServletRequest request = baseRequest();

        HeaderContext context = resolver.resolve(request);

        assertEquals("WEBFE", context.channel());
    }

    @Test
    void resolveUsesExplicitChannelWhenProvided() {
        MockHttpServletRequest request = baseRequest();
        request.addHeader("channel", "MOBI");

        HeaderContext context = resolver.resolve(request);

        assertEquals("MOBI", context.channel());
    }

    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("requestId", "REQ-1");
        request.addHeader("operatorNo", "77210021");
        request.addHeader("branchNo", "772");
        request.addHeader("workDate", "2026-07-07");
        return request;
    }
}
