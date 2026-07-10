package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockTuxedoClientWorkDateTest {
    private final MockTuxedoClient client = new MockTuxedoClient();

    @Test
    void rejectsCreateWithoutWorkDate() {
        TuxedoRequest createWithoutWorkDate = request(Map.of());

        assertThat(client.call("CNAPS5701E", createWithoutWorkDate).respCode()).isEqualTo("2001");
    }

    @Test
    void preservesExplicitWorkDateOnCreate() {
        TuxedoResponse created = client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", "2026-07-10"))
        );

        assertThat(created.fields()).containsEntry("WORK_DATE", "2026-07-10");
    }

    @Test
    void updatesWorkDateWithoutChangingBillOrSerialNumber() {
        TuxedoResponse created = client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", "2026-07-10"))
        );
        Object originalBillId = created.fields().get("BILL_ID");
        Object originalSerialNo = created.fields().get("SERIAL_NO");

        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.of(
                "BILL_ID", originalBillId,
                "SERIAL_NO", "9999999",
                "WORK_DATE", "2026-07-11"
            ))
        );

        assertThat(updated.fields())
            .containsEntry("WORK_DATE", "2026-07-11")
            .containsEntry("BILL_ID", originalBillId)
            .containsEntry("SERIAL_NO", originalSerialNo);
    }

    @Test
    void preservesWorkDateWhenUpdateOmitsIt() {
        TuxedoResponse created = client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", "2026-07-10"))
        );

        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", created.fields().get("BILL_ID")))
        );

        assertThat(updated.fields()).containsEntry("WORK_DATE", "2026-07-10");
    }

    private TuxedoRequest request(Map<String, ?> fields) {
        Map<String, Object> requestFields = new LinkedHashMap<>();
        requestFields.put("REQ_ID", "SERVER-REQ");
        requestFields.put("OPERATOR_NO", "SERVER-OP");
        requestFields.put("BRANCH_NO", "SERVER-BRANCH");
        requestFields.put("PAYEE_ACCT", "622200000000000001");
        requestFields.put("PAYEE_NAME", "测试收款人");
        requestFields.put("AMOUNT", "100.00");
        requestFields.putAll(fields);
        return new TuxedoRequest(requestFields);
    }
}
