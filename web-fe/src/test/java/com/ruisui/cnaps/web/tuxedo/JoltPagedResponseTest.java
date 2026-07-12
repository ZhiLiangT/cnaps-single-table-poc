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

        assertThat(data).containsEntry("PAGE_NO", 1L).containsEntry("PAGE_SIZE", 10L).containsEntry("TOTAL", 2L);
        assertThat(records).extracting(record -> record.get("BILL_ID"))
            .containsExactly("BILL-1", "BILL-2");
        assertThat(records).extracting(record -> record.get("STATUS"))
            .containsOnly("10_PENDING_REVIEW");
        assertThat(records).extracting(record -> record.get("VERSION_NO"))
            .containsExactly(1L, 2L);
    }

    @Test
    void shapesRepeatedDictionaryLongFieldsWithoutIntApis() throws Exception {
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
            "DICTQRY",
            FakeDictionaryService.class,
            new FakeDictionaryService()
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) fields.get("_DATA");

        assertThat(items).extracting(item -> item.get("DICT_CODE"))
            .containsExactly("0", "1");
        assertThat(items).extracting(item -> item.get("SORT_NO"))
            .containsExactly(1L, 2L);
    }

    public static final class FakePagedService {
        public long getLongDef(String name, long defaultValue) {
            return switch (name) {
                case "PAGE_NO" -> 1L;
                case "PAGE_SIZE" -> 10L;
                case "TOTAL_ELEMENTS" -> 2L;
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

        public long getLongItemDef(String name, int occurrence, long defaultValue) {
            return "VERSION_NO".equals(name) && occurrence < 2 ? occurrence + 1L : defaultValue;
        }
    }

    public static final class FakeDictionaryService {
        public String getStringItemDef(String name, int occurrence, String defaultValue) {
            if ("DICT_CODE".equals(name) && occurrence < 2) {
                return String.valueOf(occurrence);
            }
            if ("DICT_TYPE".equals(name) && occurrence < 2) {
                return "FAX_FLAG";
            }
            return defaultValue;
        }

        public long getLongItemDef(String name, int occurrence, long defaultValue) {
            return "SORT_NO".equals(name) && occurrence < 2 ? occurrence + 1L : defaultValue;
        }
    }
}
