package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CnapsVoucherCreateQueryApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullCreateReturnsPendingReviewAndCanBeQueriedAndDetailed() throws Exception {
        CreatedVoucher created = createVoucher("REQ-CREATE-001", "5600.00");

        mockMvc.perform(get("/api/cnaps/vouchers")
                .header("requestId", "REQ-QRY-001")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .queryParam("status", "10_PENDING_REVIEW")
                .queryParam("operatorNo", "77210021")
                .queryParam("serialNo", created.serialNo())
                .queryParam("page", "0")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.page.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].billId").value(created.billId()))
            .andExpect(jsonPath("$.data.content[0].serialNo").value(created.serialNo()));

        mockMvc.perform(get("/api/cnaps/vouchers/{billId}", created.billId())
                .header("requestId", "REQ-DETAIL-001")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.billId").value(created.billId()))
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.lastAction").value("CREATE"))
            .andExpect(jsonPath("$.data.lastOperatorNo").value("77210021"))
            .andExpect(jsonPath("$.data.lastRequestId").value("REQ-CREATE-001"))
            .andExpect(jsonPath("$.data.payeeAccountNo").value("622200000000000001"));
    }

    @Test
    void missingPayeeAccountReturnsRequiredFieldError() throws Exception {
        String body = validCreateBody("5600.00").replace("\"payeeAccountNo\":\"622200000000000001\",", "");

        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-002")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2001"));
    }

    @Test
    void zeroAmountReturnsFormatError() throws Exception {
        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-003")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody("0.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2002"));
    }

    @Test
    void unsupportedBusinessTypeReturnsDictionaryError() throws Exception {
        String body = validCreateBody("5600.00").replace("\"businessType\":\"02102\"", "\"businessType\":\"99999\"");

        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-004")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2003"));
    }

    @Test
    void invalidPriorityReturnsDictionaryError() throws Exception {
        String body = validCreateBody("5600.00").replace("\"priority\":\"NORM\"", "\"priority\":\"FAST\"");

        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-005")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2003"));
    }

    @Test
    void missingRequestIdReturnsRequiredFieldError() throws Exception {
        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody("5600.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2001"));
    }

    @Test
    void operatorAndSerialAreFilteredBeforePagination() throws Exception {
        createVoucher("REQ-CREATE-006", "5600.00");
        CreatedVoucher second = createVoucher("REQ-CREATE-007", "5700.00");
        createVoucher("REQ-CREATE-008", "5800.00");

        mockMvc.perform(get("/api/cnaps/vouchers")
                .header("requestId", "REQ-QRY-002")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .queryParam("operatorNo", "77210021")
                .queryParam("serialNo", second.serialNo())
                .queryParam("page", "0")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.page.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].billId").value(second.billId()))
            .andExpect(jsonPath("$.data.content[0].serialNo").value(second.serialNo()));
    }

    private CreatedVoucher createVoucher(String requestId, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", requestId)
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody(amount)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.respCode").value("0000"))
            .andExpect(jsonPath("$.data.billId", startsWith("B20260707")))
            .andExpect(jsonPath("$.data.serialNo", startsWith("0002")))
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String billId = extractJsonValue(response, "billId");
        String serialNo = extractJsonValue(response, "serialNo");
        return new CreatedVoucher(billId, serialNo);
    }

    private String extractJsonValue(String response, String fieldName) {
        String markerText = "\"" + fieldName + "\":\"";
        int marker = response.indexOf(markerText) + markerText.length();
        return response.substring(marker, response.indexOf('"', marker));
    }

    static String validCreateBody(String amount) {
        return """
            {
              "businessType":"02102",
              "accountPart1":"404045",
              "accountPart2":"00772",
              "accountPart3":"000000000001",
              "accountName":"付款账户户名",
              "payerName":"付款人名称",
              "payeeAccountNo":"622200000000000001",
              "payeeName":"收款人名称",
              "priority":"NORM",
              "receiveBankNo":"102290000002",
              "receiveBankName":"接收行名称",
              "systemType":"CNAPS",
              "amount":"%s",
              "debitMode":"1",
              "feeAmount":"0.00",
              "feeChargeMode":"1",
              "sendMode":"0",
              "faxFlag":"0",
              "voucherNo":"PZ202607070001",
              "remark":"验证录入"
            }
            """.formatted(amount);
    }

    private record CreatedVoucher(String billId, String serialNo) {
    }
}
