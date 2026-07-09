package com.ruisui.cnaps.web.tuxedo;

import javax.servlet.ServletContext;

public final class TuxedoClientProvider {
    private static final String ATTRIBUTE_NAME = TuxedoClient.class.getName();

    private TuxedoClientProvider() {
    }

    public static TuxedoClient get(ServletContext context) {
        Object existing = context.getAttribute(ATTRIBUTE_NAME);
        if (existing instanceof TuxedoClient tuxedoClient) {
            return tuxedoClient;
        }

        TuxedoRuntimeConfig config = TuxedoRuntimeConfig.from(context);
        TuxedoClient created = create(config);
        context.log("CNAPS WebFE Tuxedo client mode=" + config.mode() + ", joltListen=" + config.joltListen());
        context.setAttribute(ATTRIBUTE_NAME, created);
        return created;
    }

    static TuxedoClient create(TuxedoRuntimeConfig config) {
        if ("jolt".equalsIgnoreCase(config.mode())) {
            return new JoltTuxedoClient(config);
        }
        if ("atmi".equalsIgnoreCase(config.mode())) {
            return new AtmiTuxedoClient();
        }
        return new MockTuxedoClient();
    }
}
