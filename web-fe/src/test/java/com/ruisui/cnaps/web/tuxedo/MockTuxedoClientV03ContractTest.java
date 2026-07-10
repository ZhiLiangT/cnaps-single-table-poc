package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockTuxedoClientV03ContractTest {
    private final MockTuxedoClient client = new MockTuxedoClient();

    @Test
    void rejectsNonPositiveOrOverScaledAmount() {
        assertThat(client.call("CNAPS5701E", request(Map.of("AMOUNT", "0"))).respCode())
            .isEqualTo("2002");
        assertThat(client.call("CNAPS5701E", request(Map.of("AMOUNT", "1.001"))).respCode())
            .isEqualTo("2002");
    }

    @Test
    void fixedPocOperatorCanCreateAndReviewTheSameVoucher() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        TuxedoResponse reviewed = client.call(
            "CNAPS5702A",
            request(Map.of("BILL_ID", created.fields().get("BILL_ID")))
        );

        assertThat(reviewed.respCode()).isEqualTo("0000");
        assertThat(reviewed.fields()).containsEntry("STATUS", "20_REVIEW_APPROVED");
    }

    @Test
    void returnUpdateAndDeleteFollowTheApprovedStateFlow() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");
        TuxedoResponse returned = client.call(
            "CNAPS5702R",
            request(Map.of("BILL_ID", billId, "REJECT_REASON", "户名有误"))
        );
        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", billId, "PAYEE_NAME", "修改后户名"))
        );
        TuxedoResponse deleted = client.call(
            "CNAPS5701D",
            request(Map.of("BILL_ID", billId, "DELETE_REASON", "录入错误"))
        );

        assertThat(returned.fields()).containsEntry("STATUS", "30_REVIEW_REJECTED");
        assertThat(updated.fields())
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("PAYEE_NAME", "修改后户名");
        assertThat(deleted.fields()).containsEntry("STATUS", "40_DELETED");
    }

    @Test
    void generalQueryAppliesDocumentedFiltersAndPagination() {
        TuxedoResponse first = client.call(
            "CNAPS5701E",
            request(Map.of("VOUCHER_NO", "PZ-001", "PAYEE_NAME", "甲收款人"))
        );
        client.call(
            "CNAPS5701E",
            request(Map.of("VOUCHER_NO", "PZ-002", "PAYEE_NAME", "乙收款人"))
        );

        TuxedoResponse queried = client.call(
            "CNAPS4609Q",
            request(Map.of(
                "VOUCHER_NO", "PZ-001",
                "PAYEE_NAME", "甲",
                "PAGE_NO", "1",
                "PAGE_SIZE", "1"
            ))
        );

        Map<String, Object> page = page(queried);
        assertThat(page).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 1).containsEntry("TOTAL", 1);
        assertThat(records(page)).extracting(record -> record.get("BILL_ID"))
            .containsExactly(first.fields().get("BILL_ID"));
    }

    @Test
    void deletedVoucherIsHiddenUnlessIncludeDeletedIsTrue() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");
        client.call("CNAPS5701D", request(Map.of("BILL_ID", billId)));

        assertThat(records(page(client.call("CNAPS4609Q", request(Map.of()))))).isEmpty();
        assertThat(records(page(client.call(
            "CNAPS4609Q",
            request(Map.of("INCLUDE_DELETED", "true"))
        )))).hasSize(1);
    }

    @Test
    void paginationReturnsOnlyTheRequestedStableSlice() {
        TuxedoResponse first = client.call("CNAPS5701E", request(Map.of("VOUCHER_NO", "PZ-001")));
        TuxedoResponse second = client.call("CNAPS5701E", request(Map.of("VOUCHER_NO", "PZ-002")));

        Map<String, Object> firstPage = page(client.call(
            "CNAPS4609Q",
            request(Map.of("PAGE_NO", "1", "PAGE_SIZE", "1"))
        ));
        Map<String, Object> secondPage = page(client.call(
            "CNAPS4609Q",
            request(Map.of("PAGE_NO", "2", "PAGE_SIZE", "1"))
        ));

        assertThat(firstPage).containsEntry("TOTAL", 2);
        assertThat(records(firstPage)).extracting(record -> record.get("BILL_ID"))
            .containsExactly(first.fields().get("BILL_ID"));
        assertThat(records(secondPage)).extracting(record -> record.get("BILL_ID"))
            .containsExactly(second.fields().get("BILL_ID"));
    }

    @Test
    void updateValidatesSuppliedValuesWithoutMutatingTheVoucher() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");

        TuxedoResponse invalidAmount = client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", billId, "AMOUNT", "1.001"))
        );
        TuxedoResponse invalidWorkDate = client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", billId, "WORK_DATE", "2026/07/10"))
        );
        TuxedoResponse detail = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));

        assertThat(invalidAmount.respCode()).isEqualTo("2002");
        assertThat(invalidWorkDate.respCode()).isEqualTo("2002");
        assertThat(detail.fields())
            .containsEntry("AMOUNT", "100.00")
            .containsEntry("WORK_DATE", "2026-07-10")
            .containsEntry("VERSION_NO", 1);
    }

    @Test
    void updatePreservesTrustedServerFields() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");

        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.ofEntries(
                Map.entry("BILL_ID", billId),
                Map.entry("SERIAL_NO", "9999999"),
                Map.entry("OPERATOR_NO", "CLIENT-OPERATOR"),
                Map.entry("BRANCH_NO", "CLIENT-BRANCH"),
                Map.entry("STATUS", "20_REVIEW_APPROVED"),
                Map.entry("VERSION_NO", 99),
                Map.entry("CREATED_AT", "client-created-at"),
                Map.entry("LAST_ACTION", "CLIENT-ACTION"),
                Map.entry("LAST_ACTION_TIME", "client-action-time"),
                Map.entry("PAYEE_NAME", "修改后户名")
            ))
        );

        assertThat(updated.fields())
            .containsEntry("BILL_ID", created.fields().get("BILL_ID"))
            .containsEntry("SERIAL_NO", created.fields().get("SERIAL_NO"))
            .containsEntry("OPERATOR_NO", "77210021")
            .containsEntry("BRANCH_NO", "772")
            .containsEntry("CREATED_AT", created.fields().get("CREATED_AT"))
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("LAST_ACTION", "UPDATE")
            .containsEntry("VERSION_NO", 2)
            .containsEntry("PAYEE_NAME", "修改后户名");
    }

    @Test
    void blankReturnReasonDoesNotMutateTheVoucher() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");

        TuxedoResponse returned = client.call(
            "CNAPS5702R",
            request(Map.of("BILL_ID", billId, "REJECT_REASON", " "))
        );
        TuxedoResponse detail = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));

        assertThat(returned.respCode()).isEqualTo("2001");
        assertThat(detail.fields())
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("VERSION_NO", 1);
    }

    @Test
    void invalidStatesReturnTheDocumentedOperationErrors() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");
        client.call("CNAPS5702A", request(Map.of("BILL_ID", billId)));

        assertThat(client.call("CNAPS5701U", request(Map.of("BILL_ID", billId))).respCode())
            .isEqualTo("3003");
        assertThat(client.call("CNAPS5701D", request(Map.of("BILL_ID", billId))).respCode())
            .isEqualTo("3003");
        assertThat(client.call("CNAPS5702A", request(Map.of("BILL_ID", billId))).respCode())
            .isEqualTo("3004");
        assertThat(client.call(
            "CNAPS5702R",
            request(Map.of("BILL_ID", billId, "REJECT_REASON", "退回"))
        ).respCode()).isEqualTo("3004");
    }

    @Test
    void knownDictionaryReturnsDocumentedItems() {
        TuxedoResponse response = client.call("DICTQRY", request(Map.of("DICT_TYPE", "FAX_FLAG")));

        assertThat(response.respCode()).isEqualTo("0000");
        assertThat(dictionaryItems(response))
            .extracting(item -> item.get("DICT_CODE"), item -> item.get("DICT_NAME"))
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("0", "否"),
                org.assertj.core.groups.Tuple.tuple("1", "是")
            );
    }

    @Test
    void unknownDictionaryReturnsInvalidDictionaryError() {
        assertThat(client.call("DICTQRY", request(Map.of("DICT_TYPE", "UNKNOWN"))).respCode())
            .isEqualTo("2003");
    }

    @Test
    void nonMatchingBankFilterReturnsAnEmptyPage() {
        TuxedoResponse response = client.call(
            "BANKQRY",
            request(Map.of("BANK_NO", "999999999999", "PAGE_NO", "1", "PAGE_SIZE", "5"))
        );

        assertThat(page(response)).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 5).containsEntry("TOTAL", 0);
        assertThat(records(page(response))).isEmpty();
    }

    private TuxedoRequest request(Map<String, ?> overrides) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("REQ_ID", "REQ-POC");
        fields.put("OPERATOR_NO", "77210021");
        fields.put("BRANCH_NO", "772");
        fields.put("WORK_DATE", "2026-07-10");
        fields.put("BUSINESS_TYPE", "02102");
        fields.put("ACCOUNT_PART1", "404045");
        fields.put("ACCOUNT_PART2", "00772");
        fields.put("ACCOUNT_PART3", "000000000001");
        fields.put("PAYEE_ACCT", "622200000000000001");
        fields.put("PAYEE_NAME", "测试收款人");
        fields.put("PRIORITY", "NORM");
        fields.put("SYSTEM_TYPE", "CNAPS");
        fields.put("AMOUNT", "100.00");
        fields.putAll(overrides);
        return new TuxedoRequest(fields);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(TuxedoResponse response) {
        return (Map<String, Object>) response.fields().get("_DATA");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("RECORDS");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dictionaryItems(TuxedoResponse response) {
        return (List<Map<String, Object>>) response.fields().get("_DATA");
    }
}
