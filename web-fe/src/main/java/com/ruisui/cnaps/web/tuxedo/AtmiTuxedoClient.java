package com.ruisui.cnaps.web.tuxedo;

public class AtmiTuxedoClient implements TuxedoClient {
    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        return TuxedoResponse.fail("4003", "Native ATMI client is not configured in this local build");
    }
}
