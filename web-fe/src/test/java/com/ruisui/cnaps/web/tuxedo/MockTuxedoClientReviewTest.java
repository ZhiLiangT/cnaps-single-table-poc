package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MockTuxedoClientReviewTest {
    private final MockTuxedoClient client = new MockTuxedoClient();

    @Test
    void returnsModifyAndReviewAgainThroughThePendingState() {
        String billId = createVoucher("V-RETURN");

        TuxedoResponse returned = client.call("CNAPS5702R", request(Map.of("BILL_ID", billId)));

        assertThat(returned.success()).isTrue();
        assertThat(returned.fields().keySet()).containsExactlyInAnyOrder(
            "BILL_ID", "STATUS", "CHECKER_NO", "CHECKER_TIME", "LAST_ACTION", "VERSION_NO"
        );
        assertThat(returned.fields())
            .containsEntry("BILL_ID", billId)
            .containsEntry("STATUS", "30_REVIEW_REJECTED")
            .containsEntry("CHECKER_NO", "77210021")
            .containsEntry("LAST_ACTION", "REVIEW_RETURN")
            .containsEntry("VERSION_NO", 2);
        assertThat(returned.fields().get("CHECKER_TIME")).isNotNull();

        TuxedoResponse updated = client.call("CNAPS5701U", request(Map.of(
            "BILL_ID", billId,
            "AMOUNT", "120.00"
        )));

        assertThat(updated.success()).isTrue();
        assertThat(updated.fields())
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("LAST_ACTION", "UPDATE")
            .containsEntry("VERSION_NO", 3);
        assertThat(updated.fields()).doesNotContainKeys("CHECKER_NO", "CHECKER_TIME");

        TuxedoResponse approved = client.call("CNAPS5702A", request(Map.of("BILL_ID", billId)));

        assertThat(approved.success()).isTrue();
        assertThat(approved.fields())
            .containsEntry("STATUS", "20_REVIEW_APPROVED")
            .containsEntry("LAST_ACTION", "REVIEW_PASS")
            .containsEntry("VERSION_NO", 4);
    }

    @Test
    void rejectsRepeatedReviewAndMissingVoucher() {
        String billId = createVoucher("V-REPEAT");

        assertThat(client.call("CNAPS5702A", request(Map.of("BILL_ID", billId))).success()).isTrue();

        TuxedoResponse repeated = client.call("CNAPS5702A", request(Map.of("BILL_ID", billId)));
        TuxedoResponse missing = client.call("CNAPS5702R", request(Map.of("BILL_ID", "B-MISSING")));

        assertThat(repeated.success()).isFalse();
        assertThat(repeated.respCode()).isEqualTo("3004");
        assertThat(missing.success()).isFalse();
        assertThat(missing.respCode()).isEqualTo("3001");
    }

    @Test
    void concurrentPassAndReturnAllowOnlyOneWinner() throws Exception {
        String billId = createVoucher("V-CONCURRENT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<TuxedoResponse> pass = pool.submit(() -> concurrentCall("CNAPS5702A", billId, ready, start));
            Future<TuxedoResponse> reject = pool.submit(() -> concurrentCall("CNAPS5702R", billId, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            TuxedoResponse first = pass.get(2, TimeUnit.SECONDS);
            TuxedoResponse second = reject.get(2, TimeUnit.SECONDS);
            TuxedoResponse detail = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));

            assertThat(first.success() ^ second.success()).isTrue();
            assertThat(first.success() ? second.respCode() : first.respCode()).isEqualTo("3004");
            assertThat(detail.fields().get("VERSION_NO")).isEqualTo(2);
            assertThat(detail.fields().get("STATUS"))
                .isIn("20_REVIEW_APPROVED", "30_REVIEW_REJECTED");
        } finally {
            pool.shutdownNow();
        }
    }

    private TuxedoResponse concurrentCall(
        String serviceName,
        String billId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(2, TimeUnit.SECONDS);
        return client.call(serviceName, request(Map.of("BILL_ID", billId)));
    }

    private String createVoucher(String voucherNo) {
        java.util.LinkedHashMap<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("WORK_DATE", "2026-07-15");
        fields.put("BUSINESS_TYPE", "02102");
        fields.put("ACCOUNT_PART1", "404045");
        fields.put("ACCOUNT_PART2", "00772");
        fields.put("ACCOUNT_PART3", "000000000001");
        fields.put("PAYEE_ACCT", "622200000000000001");
        fields.put("PAYEE_NAME", "审核POC收款人");
        fields.put("PRIORITY", "NORM");
        fields.put("SYSTEM_TYPE", "CNAPS");
        fields.put("AMOUNT", "100.00");
        fields.put("VOUCHER_NO", voucherNo);

        TuxedoResponse response = client.call("CNAPS5701E", request(fields));
        assertThat(response.success()).isTrue();
        return String.valueOf(response.fields().get("BILL_ID"));
    }

    private TuxedoRequest request(Map<String, ?> fields) {
        java.util.LinkedHashMap<String, Object> requestFields = new java.util.LinkedHashMap<>();
        requestFields.put("REQ_ID", "REQ-TEST");
        requestFields.put("OPERATOR_NO", "77210021");
        requestFields.put("BRANCH_NO", "772");
        requestFields.putAll(fields);
        return new TuxedoRequest(requestFields);
    }
}
