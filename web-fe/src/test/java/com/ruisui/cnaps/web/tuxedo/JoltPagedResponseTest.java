package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JoltPagedResponseTest {
    @Test
    void shapesRepeatedVoucherFieldsIntoRecords() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
        Method method = JoltTuxedoClient.class.getDeclaredMethod(
            "readResponseFields",
            String.class,
            Class.class,
            Object.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) method.invoke(
            client,
            "CNAPS4609Q",
            FakePagedService.class,
            new FakePagedService()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) fields.get("_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("RECORDS");

        assertThat(data).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 10).containsEntry("TOTAL", 2);
        assertThat(records).extracting(record -> record.get("BILL_ID"))
            .containsExactly("BILL-1", "BILL-2");
        assertThat(records).extracting(record -> record.get("STATUS"))
            .containsOnly("10_PENDING_REVIEW");
    }

    public static final class FakePagedService {
        public int getIntDef(String name, int defaultValue) {
            return switch (name) {
                case "PAGE_NO" -> 1;
                case "PAGE_SIZE" -> 10;
                case "TOTAL_ELEMENTS" -> 2;
                default -> defaultValue;
            };
        }

        public String getStringItemDef(String name, int occurrence, String defaultValue) {
            if ("BILL_ID".equals(name) && occurrence < 2) {
                return "BILL-" + (occurrence + 1);
            }
            if ("STATUS".equals(name) && occurrence < 2) {
                return "10_PENDING_REVIEW";
            }
            return defaultValue;
        }
    }
}
