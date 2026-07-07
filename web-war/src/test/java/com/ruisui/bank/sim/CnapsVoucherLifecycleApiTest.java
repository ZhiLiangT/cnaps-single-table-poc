package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CnapsVoucherLifecycleApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reviewPassChangesStatusAndRepeatedReviewReturnsStatusChanged() throws Exception {
        String billId = createVoucher("REQ-LIFE-001", "77210021");

        reviewPass(billId, "REQ-LIFE-002", "77210022")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.respMsg").value("操作已成功"))
            .andExpect(jsonPath("$.data.checkerNo").value("77210022"))
            .andExpect(jsonPath("$.data.status").value("20_REVIEW_APPROVED"))
            .andExpect(jsonPath("$.data.lastOperatorNo").value("77210022"))
            .andExpect(jsonPath("$.data.lastRequestId").value("REQ-LIFE-002"));

        reviewPass(billId, "REQ-LIFE-003", "77210022")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3004"));
    }

    @Test
    void inputOperatorCannotReviewOwnVoucher() throws Exception {
        String billId = createVoucher("REQ-LIFE-004", "77210021");

        reviewPass(billId, "REQ-LIFE-005", "77210021")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3005"));
    }

    @Test
    void makerSelfReviewAfterApprovalReturnsThreeZeroZeroFiveForReviewPass() throws Exception {
        String billId = createVoucher("REQ-LIFE-014", "77210021");

        reviewPass(billId, "REQ-LIFE-015", "77210022").andExpect(status().isOk());

        reviewPass(billId, "REQ-LIFE-016", "77210021")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3005"));
    }

    @Test
    void makerSelfReviewAfterApprovalReturnsThreeZeroZeroFiveForReviewReturn() throws Exception {
        String billId = createVoucher("REQ-LIFE-017", "77210021");

        reviewPass(billId, "REQ-LIFE-018", "77210022").andExpect(status().isOk());

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/review-return", billId)
                .header("requestId", "REQ-LIFE-019")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"自审退回\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3005"));
    }

    @Test
    void reviewReturnStoresReasonAndUpdateResubmitsPendingReview() throws Exception {
        String billId = createVoucher("REQ-LIFE-006", "77210021");

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/review-return", billId)
                .header("requestId", "REQ-LIFE-007")
                .header("operatorNo", "77210022")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"收款人信息需修正\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("30_REVIEW_REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value("收款人信息需修正"));

        mockMvc.perform(put("/api/cnaps/vouchers/{billId}", billId)
                .header("requestId", "REQ-LIFE-008")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CnapsVoucherCreateQueryApiTest.validCreateBody("6600.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.rejectReason").doesNotExist())
            .andExpect(jsonPath("$.data.versionNo").value(3));
    }

    @Test
    void deletePendingVoucherAndRejectApprovedVoucherDelete() throws Exception {
        String pendingBillId = createVoucher("REQ-LIFE-009", "77210021");

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/delete", pendingBillId)
                .header("requestId", "REQ-LIFE-010")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deleteReason\":\"录入有误\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("40_DELETED"))
            .andExpect(jsonPath("$.data.deleteOperatorNo").value("77210021"))
            .andExpect(jsonPath("$.data.lastOperatorNo").value("77210021"))
            .andExpect(jsonPath("$.data.lastRequestId").value("REQ-LIFE-010"));

        String approvedBillId = createVoucher("REQ-LIFE-011", "77210021");
        reviewPass(approvedBillId, "REQ-LIFE-012", "77210022").andExpect(status().isOk());

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/delete", approvedBillId)
                .header("requestId", "REQ-LIFE-013")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deleteReason\":\"尝试删除已复核\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3003"));
    }

    private String createVoucher(String requestId, String operatorNo) throws Exception {
        String response = mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", requestId)
                .header("operatorNo", operatorNo)
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CnapsVoucherCreateQueryApiTest.validCreateBody("5600.00")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        int marker = response.indexOf("\"billId\":\"") + 10;
        return response.substring(marker, response.indexOf('"', marker));
    }

    private ResultActions reviewPass(String billId, String requestId, String operatorNo) throws Exception {
        return mockMvc.perform(post("/api/cnaps/vouchers/{billId}/review-pass", billId)
            .header("requestId", requestId)
            .header("operatorNo", operatorNo)
            .header("branchNo", "772")
            .header("workDate", "2026-07-07")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewComment\":\"复核通过\"}"));
    }
}
