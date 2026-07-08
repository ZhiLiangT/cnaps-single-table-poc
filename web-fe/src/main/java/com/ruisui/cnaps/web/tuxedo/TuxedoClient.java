package com.ruisui.cnaps.web.tuxedo;

public interface TuxedoClient {
    TuxedoResponse call(String serviceName, TuxedoRequest request);
}
