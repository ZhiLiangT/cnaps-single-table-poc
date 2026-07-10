package com.ruisui.cnaps.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyRespCodeRespMsgAndData() throws Exception {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(
            "查询成功",
            Map.of("status", "10_PENDING_REVIEW")
        );

        assertThat(ApiResponse.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("respCode", "respMsg", "data");
        assertThat(objectMapper.writeValueAsString(response))
            .isEqualTo("{\"respCode\":\"0000\",\"respMsg\":\"查询成功\",\"data\":{\"status\":\"10_PENDING_REVIEW\"}}");
    }

    @Test
    void failureUsesNullData() {
        assertThat(ApiResponse.fail("3001", "单据不存在"))
            .isEqualTo(new ApiResponse<>("3001", "单据不存在", null));
    }
}
