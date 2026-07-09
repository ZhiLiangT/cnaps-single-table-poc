package com.ruisui.cnaps.web.tuxedo;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

public record TuxedoRuntimeConfig(
    String mode,
    String appHome,
    String joltListen,
    int joltTimeoutMillis,
    String joltUserName,
    String joltUserRole,
    String joltUserPassword,
    String joltAppPassword
) {
    private static final String DEFAULT_APP_HOME = "/opt/ruisui-bank-sim";
    private static final String DEFAULT_JOLT_LISTEN = "//127.0.0.1:8000";
    private static final int DEFAULT_JOLT_TIMEOUT_MILLIS = 30000;

    public static TuxedoRuntimeConfig from(ServletContext context) {
        Properties appProperties = loadAppProperties(context);
        return resolve(appProperties, System::getProperty, System::getenv, context::getInitParameter);
    }

    static TuxedoRuntimeConfig defaults(String mode) {
        return new TuxedoRuntimeConfig(
            valueOrDefault(mode, "mock"),
            DEFAULT_APP_HOME,
            DEFAULT_JOLT_LISTEN,
            DEFAULT_JOLT_TIMEOUT_MILLIS,
            null,
            null,
            null,
            null
        );
    }

    static TuxedoRuntimeConfig resolve(
        Properties appProperties,
        Function<String, String> systemProperty,
        Function<String, String> environment,
        Function<String, String> contextParameter
    ) {
        String mode = resolveValue(
            "webfe.tuxedo.client.mode",
            "WEBFE_TUXEDO_CLIENT_MODE",
            "tuxedo.client.mode",
            "mock",
            appProperties,
            systemProperty,
            environment,
            contextParameter
        );
        String appHome = resolveValue(
            "app.home",
            "APP_HOME",
            "app.home",
            DEFAULT_APP_HOME,
            appProperties,
            systemProperty,
            environment,
            contextParameter
        );
        String joltListen = resolveValue(
            "webfe.tuxedo.jolt.listen",
            "WEBFE_TUXEDO_JOLT_LISTEN",
            "tuxedo.jolt.listen",
            DEFAULT_JOLT_LISTEN,
            appProperties,
            systemProperty,
            environment,
            contextParameter
        );
        String timeout = resolveValue(
            "webfe.tuxedo.jolt.timeoutMillis",
            "WEBFE_TUXEDO_JOLT_TIMEOUT_MILLIS",
            "tuxedo.jolt.timeoutMillis",
            String.valueOf(DEFAULT_JOLT_TIMEOUT_MILLIS),
            appProperties,
            systemProperty,
            environment,
            contextParameter
        );

        return new TuxedoRuntimeConfig(
            mode,
            appHome,
            joltListen,
            parsePositiveInt(timeout, DEFAULT_JOLT_TIMEOUT_MILLIS),
            nullable(resolveValue("webfe.tuxedo.jolt.userName", "WEBFE_TUXEDO_JOLT_USER_NAME", "tuxedo.jolt.userName", null, appProperties, systemProperty, environment, contextParameter)),
            nullable(resolveValue("webfe.tuxedo.jolt.userRole", "WEBFE_TUXEDO_JOLT_USER_ROLE", "tuxedo.jolt.userRole", null, appProperties, systemProperty, environment, contextParameter)),
            nullable(resolveValue("webfe.tuxedo.jolt.userPassword", "WEBFE_TUXEDO_JOLT_USER_PASSWORD", "tuxedo.jolt.userPassword", null, appProperties, systemProperty, environment, contextParameter)),
            nullable(resolveValue("webfe.tuxedo.jolt.appPassword", "WEBFE_TUXEDO_JOLT_APP_PASSWORD", "tuxedo.jolt.appPassword", null, appProperties, systemProperty, environment, contextParameter))
        );
    }

    private static Properties loadAppProperties(ServletContext context) {
        Properties properties = new Properties();
        String appHome = firstNonBlank(
            System.getProperty("app.home"),
            System.getenv("APP_HOME"),
            context.getInitParameter("app.home"),
            DEFAULT_APP_HOME
        );
        String configPath = firstNonBlank(
            System.getProperty("webfe.config"),
            System.getenv("WEBFE_CONFIG"),
            context.getInitParameter("webfe.config"),
            Path.of(appHome, "conf", "app.properties").toString()
        );

        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ex) {
            context.log("Unable to load WebFE runtime config from " + configPath, ex);
        }
        return properties;
    }

    private static String resolveValue(
        String propertyKey,
        String envKey,
        String contextKey,
        String defaultValue,
        Properties appProperties,
        Function<String, String> systemProperty,
        Function<String, String> environment,
        Function<String, String> contextParameter
    ) {
        return firstNonBlank(
            systemProperty.apply(propertyKey),
            environment.apply(envKey),
            appProperties.getProperty(propertyKey),
            contextParameter.apply(contextKey),
            defaultValue
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
