package com.ruisui.cnaps.web.tuxedo;

import bea.jolt.JoltRemoteService;
import bea.jolt.JoltSessionAttributes;
import com.ruisui.cnaps.web.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JoltTuxedoClientTest {
    @Test
    void omitsOperatorNoFromPagedVoucherQueryRequests() {
        for (String serviceName : List.of("CNAPS4609Q")) {
            JoltRemoteService.reset();
            TuxedoResponse response = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"))
                .call(
                    serviceName,
                    new TuxedoRequest(Map.of(
                        "OPERATOR_NO", "77210021",
                        "WORK_DATE", "2026-07-13"
                    ))
                );

            assertThat(response.success()).as(serviceName).isTrue();
            assertThat(JoltRemoteService.lastStrings()).as(serviceName)
                .containsEntry("WORK_DATE", "2026-07-13")
                .doesNotContainKey("OPERATOR_NO");
        }
    }

    @Test
    void keepsOperatorNoInNonListRequests() {
        JoltRemoteService.reset();
        TuxedoResponse response = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"))
            .call(
                "CNAPS5702I",
                new TuxedoRequest(Map.of(
                    "OPERATOR_NO", "77210021",
                    "BILL_ID", "BILL-1"
                ))
            );

        assertThat(response.success()).isTrue();
        assertThat(JoltRemoteService.lastStrings()).containsEntry("OPERATOR_NO", "77210021");
    }

    @Test
    void convertsConfiguredMillisecondTimeoutToCeilingJoltSeconds() {
        JoltSessionAttributes.reset();
        new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"))
            .call("SYSHEALTH", new TuxedoRequest(Map.of()));
        assertThat(JoltSessionAttributes.lastReceiveTimeout()).isEqualTo(30);

        JoltSessionAttributes.reset();
        TuxedoRuntimeConfig subSecond = new TuxedoRuntimeConfig(
            "jolt", "77210021", "772", "/opt/ruisui-bank-sim", "//127.0.0.1:8000",
            1, null, null, null, null
        );
        new JoltTuxedoClient(subSecond).call("SYSHEALTH", new TuxedoRequest(Map.of()));
        assertThat(JoltSessionAttributes.lastReceiveTimeout()).isEqualTo(1);
    }

    @Test
    void convertsMaximumPositiveMillisecondTimeoutWithoutIntegerOverflow() {
        JoltSessionAttributes.reset();
        TuxedoRuntimeConfig maximum = new TuxedoRuntimeConfig(
            "jolt", "77210021", "772", "/opt/ruisui-bank-sim", "//127.0.0.1:8000",
            Integer.MAX_VALUE, null, null, null, null
        );

        new JoltTuxedoClient(maximum).call("SYSHEALTH", new TuxedoRequest(Map.of()));

        assertThat(JoltSessionAttributes.lastReceiveTimeout()).isEqualTo(2_147_484);
    }

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
    void readsAndMapsEveryVoucherFieldFromSingleRecordService() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
        Method readResponseFields = JoltTuxedoClient.class.getDeclaredMethod(
            "readResponseFields",
            String.class,
            Class.class,
            Object.class
        );
        readResponseFields.setAccessible(true);
        Field voucherFieldsField = JoltTuxedoClient.class.getDeclaredField("VOUCHER_FIELDS");
        voucherFieldsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> voucherFields = (List<String>) voucherFieldsField.get(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) readResponseFields.invoke(
            client,
            "CNAPS5702I",
            FakeCompleteVoucherService.class,
            new FakeCompleteVoucherService()
        );
        ApiResponse<Object> response = new TuxedoResponseMapper().toApiResponse(
            "REQ-1",
            TuxedoResponse.ok("ok", fields)
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = (Map<String, Object>) response.data();

        assertThat(fields).containsKeys(voucherFields.toArray(String[]::new));
        for (String voucherField : voucherFields) {
            Object expected = "VERSION_NO".equals(voucherField) ? 7L : "value-" + voucherField;
            assertThat(mapped.values()).as(voucherField).contains(expected);
        }
        assertThat(fields).contains(
            Map.entry("PAYER_ADDRESS", "value-PAYER_ADDRESS"),
            Map.entry("PAYEE_ADDRESS", "value-PAYEE_ADDRESS"),
            Map.entry("PAYER_BANK_NAME", "value-PAYER_BANK_NAME")
        );
        assertThat(mapped)
            .containsEntry("deleteTime", "value-DELETE_TIME")
            .containsEntry("payerAddress", "value-PAYER_ADDRESS")
            .containsEntry("payeeAddress", "value-PAYEE_ADDRESS")
            .containsEntry("payerBankName", "value-PAYER_BANK_NAME");
    }

    @Test
    void reviewActionResponsesExposeOnlyTheSixFieldSummary() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
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
            "CNAPS5702A",
            FakeReviewActionService.class,
            new FakeReviewActionService()
        );
        ApiResponse<Object> response = new TuxedoResponseMapper().toApiResponse(
            "REQ-1",
            TuxedoResponse.ok("ok", fields)
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = (Map<String, Object>) response.data();

        assertThat(mapped.keySet()).containsExactlyInAnyOrder(
            "billId", "status", "checkerNo", "checkerTime", "lastAction", "versionNo"
        );
        assertThat(mapped)
            .containsEntry("billId", "value-BILL_ID")
            .containsEntry("status", "value-STATUS")
            .containsEntry("checkerNo", "value-CHECKER_NO")
            .containsEntry("checkerTime", "value-CHECKER_TIME")
            .containsEntry("lastAction", "value-LAST_ACTION")
            .containsEntry("versionNo", 2L);
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

    @Test
    void writesMetadataIntegerFieldsThroughSetInt() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
        FakeIntSetter remoteService = new FakeIntSetter();
        Method putField = JoltTuxedoClient.class.getDeclaredMethod(
            "putField",
            Class.class,
            Object.class,
            String.class,
            Object.class
        );
        putField.setAccessible(true);

        putField.invoke(client, FakeIntSetter.class, remoteService, "PAGE_NO", "100");

        assertThat(remoteService.fieldName).isEqualTo("PAGE_NO");
        assertThat(remoteService.value).isEqualTo(100);
    }

    @Test
    void returnsApplicationErrorFieldsInsteadOfTransportFailure() {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));

        TuxedoResponse response = client.call(
            "DICTQRY_APPLICATION_ERROR",
            new TuxedoRequest(Map.of("DICT_TYPE", "UNKNOWN"))
        );

        assertThat(response.success()).isFalse();
        assertThat(response.respCode()).isEqualTo("2003");
        assertThat(response.respMsg()).isEqualTo("unknown dictionary type");
    }

    @Test
    void keepsNonApplicationInvocationFailuresAsTransportErrors() {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));

        TuxedoResponse response = client.call("RUNTIME_FAILURE", new TuxedoRequest(Map.of()));

        assertThat(response.success()).isFalse();
        assertThat(response.respCode()).isEqualTo("4002");
        assertThat(response.respMsg()).contains("simulated transport failure");
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

    public static final class FakeCompleteVoucherService {
        public String getStringDef(String fieldName, String defaultValue) {
            return "VERSION_NO".equals(fieldName) ? defaultValue : "value-" + fieldName;
        }

        public int getIntDef(String fieldName, int defaultValue) {
            return "VERSION_NO".equals(fieldName) ? 7 : defaultValue;
        }
    }

    public static final class FakeReviewActionService {
        public String getStringDef(String fieldName, String defaultValue) {
            return "VERSION_NO".equals(fieldName) ? defaultValue : "value-" + fieldName;
        }

        public int getIntDef(String fieldName, int defaultValue) {
            return "VERSION_NO".equals(fieldName) ? 2 : defaultValue;
        }
    }

    public static final class FakeIntSetter {
        private String fieldName;
        private int value;

        public void setInt(String fieldName, int value) {
            this.fieldName = fieldName;
            this.value = value;
        }
    }
}
