
package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void rejectsScientificNotationAmountOnCreate() {
        assertThat(client.call("CNAPS5701E", request(Map.of("AMOUNT", "1E+3"))).respCode())
            .isEqualTo("2002");
    }

    @Test
    void rejectsScientificNotationAmountOnUpdate() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));

        assertThat(client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", created.fields().get("BILL_ID"), "AMOUNT", "1E+3"))
        ).respCode()).isEqualTo("2002");
    }

    @Test
    void rejectsInvalidFeeAmountsOnCreateAndUpdate() {
        assertThat(client.call("CNAPS5701E", request(Map.of("FEE_AMOUNT", "-0.01"))).respCode())
            .isEqualTo("2002");
        assertThat(client.call("CNAPS5701E", request(Map.of("FEE_AMOUNT", "0.001"))).respCode())
            .isEqualTo("2002");

        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        assertThat(client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", created.fields().get("BILL_ID"), "FEE_AMOUNT", "1E+3"))
        ).respCode()).isEqualTo("2002");
    }

    @Test
    void createIgnoresClientSuppliedAuditAndResponseFields() {
        TuxedoResponse created = client.call(
            "CNAPS5701E",
            request(Map.ofEntries(
                Map.entry("BILL_ID", "CLIENT-BILL"),
                Map.entry("SERIAL_NO", "9999999"),
                Map.entry("STATUS", "40_DELETED"),
                Map.entry("VERSION_NO", 99),
                Map.entry("CREATED_AT", "client-created-at"),
                Map.entry("LAST_ACTION", "CLIENT-ACTION"),
                Map.entry("CHECKER_NO", "CLIENT-CHECKER"),
                Map.entry("DELETE_TIME", "client-delete-time"),
                Map.entry("CLIENT_ONLY_FIELD", "client-only")
            ))
        );

        assertThat(created.respCode()).isEqualTo("0000");
        assertThat(created.fields())
            .doesNotContainKeys("CHECKER_NO", "DELETE_TIME", "CLIENT_ONLY_FIELD")
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("VERSION_NO", 1)
            .containsEntry("LAST_ACTION", "CREATE")
            .containsEntry("OPERATOR_NO", "77210021")
            .containsEntry("BRANCH_NO", "772");
        assertThat(created.fields().get("BILL_ID")).isNotEqualTo("CLIENT-BILL");
        assertThat(created.fields().get("SERIAL_NO")).isNotEqualTo("9999999");
        assertThat(created.fields().get("CREATED_AT")).isNotEqualTo("client-created-at");
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
    void largeVoucherPageOffsetDoesNotWrap() {
        client.call("CNAPS5701E", request(Map.of()));

        Map<String, Object> page = page(client.call(
            "CNAPS4609Q",
            request(Map.of("PAGE_NO", "65537", "PAGE_SIZE", "65536"))
        ));

        assertThat(page).containsEntry("PAGE_NO", 65537).containsEntry("PAGE_SIZE", 65536).containsEntry("TOTAL", 1);
        assertThat(records(page)).isEmpty();
    }

    @Test
    void largeBankPageOffsetDoesNotWrap() {
        Map<String, Object> page = page(client.call(
            "BANKQRY",
            request(Map.of("PAGE_NO", "65537", "PAGE_SIZE", "65536"))
        ));

        assertThat(page).containsEntry("PAGE_NO", 65537).containsEntry("PAGE_SIZE", 65536).containsEntry("TOTAL", 1);
        assertThat(records(page)).isEmpty();
    }

    @Test
    void malformedVoucherPageValuesUseSafeDefaults() {
        client.call("CNAPS5701E", request(Map.of()));

        TuxedoResponse response = callWithoutThrowing(
            "CNAPS4609Q",
            Map.of("PAGE_NO", "2147483648", "PAGE_SIZE", "not-a-number")
        );

        assertThat(page(response)).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 10).containsEntry("TOTAL", 1);
        assertThat(records(page(response))).hasSize(1);
    }

    @Test
    void malformedBankPageValuesUseSafeDefaults() {
        TuxedoResponse response = callWithoutThrowing(
            "BANKQRY",
            Map.of("PAGE_NO", "not-a-number", "PAGE_SIZE", "2147483648")
        );

        assertThat(page(response)).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 10).containsEntry("TOTAL", 1);
        assertThat(records(page(response))).hasSize(1);
    }

    @Test
    void generalQueryFiltersByVoucherNumberExactly() {
        TuxedoResponse first = client.call("CNAPS5701E", request(Map.of("VOUCHER_NO", "PZ-001")));
        client.call("CNAPS5701E", request(Map.of("VOUCHER_NO", "PZ-002")));

        List<Map<String, Object>> records = records(page(client.call(
            "CNAPS4609Q",
            request(Map.of("VOUCHER_NO", "PZ-001"))
        )));

        assertThat(records).extracting(record -> record.get("BILL_ID"))
            .containsExactly(first.fields().get("BILL_ID"));
    }

    @Test
    void generalQueryFiltersBySerialNumberExactly() {
        TuxedoResponse first = client.call("CNAPS5701E", request(Map.of()));
        client.call("CNAPS5701E", request(Map.of()));

        List<Map<String, Object>> records = records(page(client.call(
            "CNAPS4609Q",
            request(Map.of("SERIAL_NO", first.fields().get("SERIAL_NO")))
        )));

        assertThat(records).extracting(record -> record.get("BILL_ID"))
            .containsExactly(first.fields().get("BILL_ID"));
    }

    @Test
    void generalQueryFiltersByPayeeAccountExactly() {
        client.call("CNAPS5701E", request(Map.of()));
        TuxedoResponse second = client.call(
            "CNAPS5701E",
            request(Map.of("PAYEE_ACCT", "622200000000000002"))
        );

        List<Map<String, Object>> records = records(page(client.call(
            "CNAPS4609Q",
            request(Map.of("PAYEE_ACCT", "622200000000000002"))
        )));

        assertThat(records).extracting(record -> record.get("BILL_ID"))
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

    @Test
    void bankQueryAppliesKeywordCityAndSystemFilters() {
        assertThat(records(page(client.call(
            "BANKQRY",
            request(Map.of("KEYWORD", "接收", "CITY", "上海", "SYSTEM_TYPE", "CNAPS"))
        )))).hasSize(1);
        assertThat(records(page(client.call(
            "BANKQRY",
            request(Map.of("KEYWORD", "不匹配"))
        )))).isEmpty();
        assertThat(records(page(client.call(
            "BANKQRY",
            request(Map.of("CITY", "北京"))
        )))).isEmpty();
        assertThat(records(page(client.call(
            "BANKQRY",
            request(Map.of("SYSTEM_TYPE", "HVPS"))
        )))).isEmpty();
    }

    @Test
    void createRejectsMissingRequiredDictionaryFields() {
        for (String field : List.of("BUSINESS_TYPE", "PRIORITY", "SYSTEM_TYPE")) {
            Map<String, Object> fields = new LinkedHashMap<>(request(Map.of()).fields());
            fields.remove(field);

            assertThat(client.call("CNAPS5701E", new TuxedoRequest(fields)).respCode())
                .as(field)
                .isEqualTo("2001");

            fields.put(field, " ");
            assertThat(client.call("CNAPS5701E", new TuxedoRequest(fields)).respCode())
                .as("blank " + field)
                .isEqualTo("2001");
        }
    }

    @Test
    void createAndUpdateRejectUnsupportedDictionaryValues() {
        Map<String, String> invalidValues = Map.of(
            "BUSINESS_TYPE", "99999",
            "PRIORITY", "URGENT",
            "SYSTEM_TYPE", "HVPS",
            "DEBIT_MODE", "2",
            "FEE_CHARGE_MODE", "2",
            "SEND_MODE", "1",
            "FAX_FLAG", "2"
        );
        for (Map.Entry<String, String> invalid : invalidValues.entrySet()) {
            assertThat(client.call(
                "CNAPS5701E",
                request(Map.of(invalid.getKey(), invalid.getValue()))
            ).respCode()).as("create " + invalid.getKey()).isEqualTo("2003");

            TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
            assertThat(client.call(
                "CNAPS5701U",
                request(Map.of("BILL_ID", created.fields().get("BILL_ID"), invalid.getKey(), invalid.getValue()))
            ).respCode()).as("update " + invalid.getKey()).isEqualTo("2003");
        }
    }

    @Test
    void blankOptionalDictionaryValuesDefaultOnCreateAndPreserveOnUpdate() {
        Map<String, Object> blankOptional = Map.of(
            "DEBIT_MODE", " ",
            "FEE_CHARGE_MODE", "",
            "SEND_MODE", " ",
            "FAX_FLAG", ""
        );

        TuxedoResponse defaulted = client.call("CNAPS5701E", request(blankOptional));
        assertThat(defaulted.respCode()).isEqualTo("0000");
        assertThat(defaulted.fields())
            .containsEntry("DEBIT_MODE", "1")
            .containsEntry("FEE_CHARGE_MODE", "1")
            .containsEntry("SEND_MODE", "0")
            .containsEntry("FAX_FLAG", "0");

        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of("FAX_FLAG", "1")));
        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.ofEntries(
                Map.entry("BILL_ID", created.fields().get("BILL_ID")),
                Map.entry("DEBIT_MODE", " "),
                Map.entry("FEE_CHARGE_MODE", ""),
                Map.entry("SEND_MODE", " "),
                Map.entry("FAX_FLAG", "")
            ))
        );

        assertThat(updated.respCode()).isEqualTo("0000");
        assertThat(updated.fields())
            .containsEntry("DEBIT_MODE", "1")
            .containsEntry("FEE_CHARGE_MODE", "1")
            .containsEntry("SEND_MODE", "0")
            .containsEntry("FAX_FLAG", "1");
    }

    @Test
    void bankKeywordMatchesBankNumberAsWellAsName() {
        assertThat(records(page(client.call(
            "BANKQRY",
            request(Map.of("KEYWORD", "290000"))
        )))).extracting(record -> record.get("BANK_NO")).containsExactly("102290000002");
    }

    @Test
    void partyAddressFieldsRoundTripUpdateClearAndStayOutOfLists() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of(
            "PAYER_ADDRESS", "付款地址-原值",
            "PAYEE_ADDRESS", "收款地址-原值",
            "PAYER_BANK_NAME", "开户行-原值"
        )));
        Object billId = created.fields().get("BILL_ID");

        TuxedoResponse original = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));
        assertThat(original.fields())
            .containsEntry("PAYER_ADDRESS", "付款地址-原值")
            .containsEntry("PAYEE_ADDRESS", "收款地址-原值")
            .containsEntry("PAYER_BANK_NAME", "开户行-原值");

        client.call("CNAPS5701U", request(Map.of(
            "BILL_ID", billId,
            "PAYER_ADDRESS", "付款地址-新值",
            "PAYER_BANK_NAME", ""
        )));
        TuxedoResponse updated = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));
        assertThat(updated.fields())
            .containsEntry("PAYER_ADDRESS", "付款地址-新值")
            .containsEntry("PAYEE_ADDRESS", "收款地址-原值")
            .containsEntry("PAYER_BANK_NAME", "");

        for (String service : List.of("CNAPS4609Q")) {
            Map<String, Object> record = records(page(client.call(service, request(Map.of())))).get(0);
            assertThat(record).doesNotContainKeys("PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME");
        }
    }

    @Test
    void partyAddressFieldsEnforceUnicodeCharacterLimitsOnCreateAndUpdate() {
        TuxedoResponse maximum = client.call("CNAPS5701E", request(Map.of(
            "PAYER_ADDRESS", "地".repeat(256),
            "PAYEE_ADDRESS", "址".repeat(256),
            "PAYER_BANK_NAME", "行".repeat(128)
        )));
        assertThat(maximum.respCode()).isEqualTo("0000");

        assertThat(client.call("CNAPS5701E", request(Map.of(
            "PAYER_ADDRESS", "地".repeat(257)
        ))).respCode()).isEqualTo("2002");
        assertThat(client.call("CNAPS5701E", request(Map.of(
            "PAYER_BANK_NAME", "行".repeat(129)
        ))).respCode()).isEqualTo("2002");

        Object billId = maximum.fields().get("BILL_ID");
        assertThat(client.call("CNAPS5701U", request(Map.of(
            "BILL_ID", billId,
            "PAYEE_ADDRESS", "址".repeat(257)
        ))).respCode()).isEqualTo("2002");
        assertThat(client.call("CNAPS5701U", request(Map.of(
            "BILL_ID", billId,
            "PAYER_BANK_NAME", "行".repeat(129)
        ))).respCode()).isEqualTo("2002");
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

    private TuxedoResponse callWithoutThrowing(String serviceName, Map<String, ?> overrides) {
        AtomicReference<TuxedoResponse> response = new AtomicReference<>();
        assertThatCode(() -> response.set(client.call(serviceName, request(overrides))))
            .doesNotThrowAnyException();
        return response.get();
    }
}
