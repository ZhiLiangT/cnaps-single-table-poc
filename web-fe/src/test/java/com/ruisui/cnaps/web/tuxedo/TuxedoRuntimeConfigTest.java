package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TuxedoRuntimeConfigTest {
    @Test
    void resolvesPocOperatorNumberFromRuntimeSourcesAndDefault() {
        Properties appProperties = new Properties();
        appProperties.setProperty("webfe.poc.operatorNo", "APP-OP");

        TuxedoRuntimeConfig fromSystem = TuxedoRuntimeConfig.resolve(
            appProperties,
            key -> "webfe.poc.operatorNo".equals(key) ? "SYS-OP" : null,
            env(Map.of("POC_OPERATOR_NO", "ENV-OP")),
            key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null
        );
        assertThat(fromSystem.pocOperatorNo()).isEqualTo("SYS-OP");

        TuxedoRuntimeConfig fromEnvironment = TuxedoRuntimeConfig.resolve(
            appProperties, key -> null, env(Map.of("POC_OPERATOR_NO", "ENV-OP")),
            key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
        assertThat(fromEnvironment.pocOperatorNo()).isEqualTo("ENV-OP");

        TuxedoRuntimeConfig fromProperties = TuxedoRuntimeConfig.resolve(
            appProperties, key -> null, env(Map.of()),
            key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
        assertThat(fromProperties.pocOperatorNo()).isEqualTo("APP-OP");

        TuxedoRuntimeConfig fromContext = TuxedoRuntimeConfig.resolve(
            new Properties(), key -> null, env(Map.of()),
            key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
        assertThat(fromContext.pocOperatorNo()).isEqualTo("CTX-OP");

        TuxedoRuntimeConfig defaults = TuxedoRuntimeConfig.resolve(
            new Properties(), key -> null, env(Map.of()), key -> null);
        assertThat(defaults.pocOperatorNo()).isEqualTo("77210021");
    }

    @Test
    void resolvesModeFromSystemThenEnvThenAppPropertiesThenContextThenMock() {
        Properties appProperties = new Properties();
        appProperties.setProperty("webfe.tuxedo.client.mode", "jolt");

        TuxedoRuntimeConfig config = TuxedoRuntimeConfig.resolve(
            appProperties,
            key -> "webfe.tuxedo.client.mode".equals(key) ? "atmi" : null,
            env(Map.of("WEBFE_TUXEDO_CLIENT_MODE", "mock")),
            key -> "tuxedo.client.mode".equals(key) ? "context-mock" : null
        );

        assertThat(config.mode()).isEqualTo("atmi");

        TuxedoRuntimeConfig fromEnv = TuxedoRuntimeConfig.resolve(
            appProperties,
            key -> null,
            env(Map.of("WEBFE_TUXEDO_CLIENT_MODE", "mock")),
            key -> "tuxedo.client.mode".equals(key) ? "context-mock" : null
        );

        assertThat(fromEnv.mode()).isEqualTo("mock");

        TuxedoRuntimeConfig fromAppProperties = TuxedoRuntimeConfig.resolve(
            appProperties,
            key -> null,
            env(Map.of()),
            key -> "tuxedo.client.mode".equals(key) ? "context-mock" : null
        );

        assertThat(fromAppProperties.mode()).isEqualTo("jolt");

        TuxedoRuntimeConfig fromContext = TuxedoRuntimeConfig.resolve(
            new Properties(),
            key -> null,
            env(Map.of()),
            key -> "tuxedo.client.mode".equals(key) ? "context-mock" : null
        );

        assertThat(fromContext.mode()).isEqualTo("context-mock");

        TuxedoRuntimeConfig defaults = TuxedoRuntimeConfig.resolve(
            new Properties(),
            key -> null,
            env(Map.of()),
            key -> null
        );

        assertThat(defaults.mode()).isEqualTo("mock");
    }

    @Test
    void resolvesJoltListenTimeoutAndCredentialsFromRuntimeSources() {
        TuxedoRuntimeConfig config = TuxedoRuntimeConfig.resolve(
            new Properties(),
            key -> switch (key) {
                case "webfe.tuxedo.jolt.listen" -> "//192.168.84.134:8000";
                case "webfe.tuxedo.jolt.timeoutMillis" -> "12000";
                case "webfe.tuxedo.jolt.userName" -> "cnaps";
                case "webfe.tuxedo.jolt.userRole" -> "operator";
                case "webfe.tuxedo.jolt.userPassword" -> "secret";
                case "webfe.tuxedo.jolt.appPassword" -> "app-secret";
                default -> null;
            },
            env(Map.of()),
            key -> null
        );

        assertThat(config.joltListen()).isEqualTo("//192.168.84.134:8000");
        assertThat(config.joltTimeoutMillis()).isEqualTo(12000);
        assertThat(config.joltUserName()).isEqualTo("cnaps");
        assertThat(config.joltUserRole()).isEqualTo("operator");
        assertThat(config.joltUserPassword()).isEqualTo("secret");
        assertThat(config.joltAppPassword()).isEqualTo("app-secret");
    }

    private static Function<String, String> env(Map<String, String> values) {
        return values::get;
    }
}
