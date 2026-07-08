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

        TuxedoClient created = create(context.getInitParameter("tuxedo.client.mode"));
        context.setAttribute(ATTRIBUTE_NAME, created);
        return created;
    }

    private static TuxedoClient create(String mode) {
        if ("jolt".equalsIgnoreCase(mode)) {
            return new JoltTuxedoClient();
        }
        if ("atmi".equalsIgnoreCase(mode)) {
            return new AtmiTuxedoClient();
        }
        return new MockTuxedoClient();
    }
}
