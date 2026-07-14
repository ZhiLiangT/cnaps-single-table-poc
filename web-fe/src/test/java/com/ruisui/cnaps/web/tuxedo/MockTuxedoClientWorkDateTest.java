package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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
    void rejectsExtendedYearWorkDateOnCreate() {
        assertThat(client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", "+10000-01-01"))
        ).respCode()).isEqualTo("2002");
    }

    @Test
    void rejectsExtendedYearWorkDateOnUpdate() {
        TuxedoResponse created = client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", "2026-07-10"))
        );

        assertThat(client.call(
            "CNAPS5701U",
            request(Map.of(
                "BILL_ID", created.fields().get("BILL_ID"),
                "WORK_DATE", "+10000-01-01"
            ))
        ).respCode()).isEqualTo("2002");
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

    @Test
    void voucherQueriesDoNotUseWorkDateAsAnExactFilter() {
        createVoucher("2026-07-10", "SERVER-BRANCH");
        createVoucher("2026-07-11", "SERVER-BRANCH");
        createVoucher("2026-07-10", "OTHER-BRANCH");

        for (String service : List.of("CNAPS4609Q")) {
            assertThat(records(client.call(
                service,
                request(Map.of("WORK_DATE", "2026-07-10", "BRANCH_NO", "SERVER-BRANCH"))
            )))
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-10", "2026-07-11");
        }
    }

    @Test
    void voucherQueriesIncludeBothWorkDateRangeBoundaries() {
        createVoucher("2026-07-09", "SERVER-BRANCH");
        createVoucher("2026-07-10", "SERVER-BRANCH");
        createVoucher("2026-07-12", "SERVER-BRANCH");
        createVoucher("2026-07-13", "SERVER-BRANCH");

        for (String service : List.of("CNAPS4609Q")) {
            List<Map<String, Object>> records = records(client.call(
                service,
                request(Map.of(
                    "START_WORK_DATE", "2026-07-10",
                    "END_WORK_DATE", "2026-07-12"
                ))
            ));

            assertThat(records)
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-10", "2026-07-12");
        }
    }

    @Test
    void voucherQueriesSupportOneSidedRangesAndAllDateDefault() {
        createVoucher("2026-07-09", "SERVER-BRANCH");
        createVoucher("2026-07-10", "SERVER-BRANCH");
        createVoucher("2026-07-12", "SERVER-BRANCH");
        createVoucher("2026-07-13", "SERVER-BRANCH");

        for (String service : List.of("CNAPS4609Q")) {
            assertThat(records(client.call(
                service,
                request(Map.of("START_WORK_DATE", "2026-07-10"))
            )))
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-10", "2026-07-12", "2026-07-13");
            assertThat(records(client.call(
                service,
                request(Map.of("END_WORK_DATE", "2026-07-12"))
            )))
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-09", "2026-07-10", "2026-07-12");
            assertThat(records(client.call(service, request(Map.of()))))
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-09", "2026-07-10", "2026-07-12", "2026-07-13");
        }
    }

    @Test
    void voucherQueriesTreatBlankRangeBoundsAsAbsent() {
        createVoucher("2026-07-09", "SERVER-BRANCH");
        createVoucher("2026-07-13", "SERVER-BRANCH");

        for (String service : List.of("CNAPS4609Q")) {
            TuxedoResponse response = client.call(
                service,
                request(Map.of("START_WORK_DATE", " ", "END_WORK_DATE", ""))
            );

            assertThat(response.respCode()).as(service).isEqualTo("0000");
            assertThat(records(response))
                .extracting(record -> record.get("WORK_DATE"))
                .containsExactly("2026-07-09", "2026-07-13");
        }
    }

    @Test
    void voucherQueriesRejectInvalidWorkDateRanges() {
        List<Map<String, ?>> invalidFilters = List.of(
            Map.of("START_WORK_DATE", "2026/07/10"),
            Map.of("END_WORK_DATE", "2026-02-30"),
            Map.of("START_WORK_DATE", "2026-07-12", "END_WORK_DATE", "2026-07-10")
        );

        for (String service : List.of("CNAPS4609Q")) {
            for (Map<String, ?> filter : invalidFilters) {
                assertThat(client.call(service, request(filter)).respCode())
                    .as(service + " " + filter)
                    .isEqualTo("2002");
            }
        }
    }

    @Test
    void rejectsYearZeroAndTrailingDataAcrossCreateUpdateAndQueries() {
        for (String invalidWorkDate : List.of("0000-01-01", "2026-07-10-extra")) {
            assertThat(client.call(
                "CNAPS5701E",
                request(Map.of("WORK_DATE", invalidWorkDate))
            ).respCode()).as("create " + invalidWorkDate).isEqualTo("2002");

            TuxedoResponse created = client.call(
                "CNAPS5701E",
                request(Map.of("WORK_DATE", "2026-07-10"))
            );
            assertThat(client.call(
                "CNAPS5701U",
                request(Map.of(
                    "BILL_ID", created.fields().get("BILL_ID"),
                    "WORK_DATE", invalidWorkDate
                ))
            ).respCode()).as("update " + invalidWorkDate).isEqualTo("2002");
        }
    }

    private TuxedoResponse createVoucher(String workDate, String branchNo) {
        return client.call(
            "CNAPS5701E",
            request(Map.of("WORK_DATE", workDate, "BRANCH_NO", branchNo))
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(TuxedoResponse response) {
        Map<String, Object> page = (Map<String, Object>) response.fields().get("_DATA");
        return (List<Map<String, Object>>) page.get("RECORDS");
    }

    private TuxedoRequest request(Map<String, ?> fields) {
        Map<String, Object> requestFields = new LinkedHashMap<>();
        requestFields.put("REQ_ID", "SERVER-REQ");
        requestFields.put("OPERATOR_NO", "SERVER-OP");
        requestFields.put("BRANCH_NO", "SERVER-BRANCH");
        requestFields.put("BUSINESS_TYPE", "02102");
        requestFields.put("ACCOUNT_PART1", "404045");
        requestFields.put("ACCOUNT_PART2", "00772");
        requestFields.put("ACCOUNT_PART3", "000000000001");
        requestFields.put("PAYEE_ACCT", "622200000000000001");
        requestFields.put("PAYEE_NAME", "测试收款人");
        requestFields.put("PRIORITY", "NORM");
        requestFields.put("SYSTEM_TYPE", "CNAPS");
        requestFields.put("AMOUNT", "100.00");
        requestFields.putAll(fields);
        return new TuxedoRequest(requestFields);
    }
}
