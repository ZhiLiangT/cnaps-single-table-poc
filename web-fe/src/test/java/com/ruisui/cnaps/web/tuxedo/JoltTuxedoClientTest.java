package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JoltTuxedoClientTest {
    @Test
    void extractsWorkDateFromJoltResponse() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
        FakeRemoteService remoteService = new FakeRemoteService();
        Method readResponseFields = JoltTuxedoClient.class.getDeclaredMethod(
            "readResponseFields",
            String.class,
            Class.class,
            Object.class
        );
        readResponseFields.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) readResponseFields.invoke(
            client,
            "CNAPS5702I",
            FakeRemoteService.class,
            remoteService
        );

        assertThat(fields).containsEntry("WORK_DATE", "2026-07-11");
    }

    @Test
    void returnsUnavailableWhenJoltRuntimeClassesAreMissing() {
        TuxedoRuntimeConfig config = TuxedoRuntimeConfig.defaults("jolt");
        JoltTuxedoClient client = new JoltTuxedoClient(config, new EmptyClassLoader());

        TuxedoResponse response = client.call("SYSHEALTH", new TuxedoRequest(Map.of("REQ_ID", "REQ-1")));

        assertThat(response.success()).isFalse();
        assertThat(response.respCode()).isEqualTo("4003");
        assertThat(response.respMsg()).contains("Jolt runtime classes not found");
    }

    private static final class EmptyClassLoader extends ClassLoader {
        private EmptyClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("bea.jolt.")) {
                throw new ClassNotFoundException(name);
            }
            return JoltTuxedoClientTest.class.getClassLoader().loadClass(name);
        }
    }

    public static final class FakeRemoteService {
        public String getStringDef(String fieldName, String defaultValue) {
            return "WORK_DATE".equals(fieldName) ? "2026-07-11" : defaultValue;
        }
    }
}
