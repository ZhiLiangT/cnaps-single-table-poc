package com.ruisui.cnaps.web.tuxedo;

public class AtmiTuxedoClient implements TuxedoClient {
    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        return TuxedoResponse.fail("4003", "Tuxedo服务不可用：" + serviceName);
    }
}
