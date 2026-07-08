package com.ruisui.cnaps.web.tuxedo;

public class JoltTuxedoClient implements TuxedoClient {
    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        return TuxedoResponse.fail("4003", "Jolt client is not configured in this local build");
    }
}
