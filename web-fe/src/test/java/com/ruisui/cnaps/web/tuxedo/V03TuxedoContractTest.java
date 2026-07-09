package com.ruisui.cnaps.web.tuxedo;

import com.ruisui.cnaps.web.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V03TuxedoContractTest {
    private final TuxedoRequestMapper requestMapper = new TuxedoRequestMapper();
    private final TuxedoResponseMapper responseMapper = new TuxedoResponseMapper();

    @Test
    void mapsV03HttpFieldsToCanonicalTuxedoFields() {
        TuxedoRequest request = requestMapper.from(
            "REQ-V03-FE-001",
            "77210021",
            "772",
            "2026-07-07",
            Map.of(
                "pageNo", "1",
                "pageSize", "10",
                "receiveBankNo", "102290000002",
                "payeeAccountNo", "622200000000000001",
                "includeDeleted", "false"
            )
        );

        assertThat(request.fields())
            .containsEntry("REQ_ID", "REQ-V03-FE-001")
            .containsEntry("PAGE_NO", "1")
            .containsEntry("PAGE_SIZE", "10")
            .containsEntry("RECEIVE_BANK_NO", "102290000002")
            .containsEntry("PAYEE_ACCT", "622200000000000001")
            .containsEntry("INCLUDE_DELETED", "false");
    }

    @Test
    void mapsTuxedoHealthResponseToV03JsonNames() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("WEBFE", "UP");
        fields.put("TUXEDO", "UP");
        fields.put("ORACLE", "UP");
        fields.put("SERVICE", "SYSHEALTH");
        fields.put("CHECK_TIME", "2026-07-07 10:00:00");

        ApiResponse<Object> response = responseMapper.toApiResponse(
            "REQ-V03-FE-002",
            TuxedoResponse.ok("健康检查成功", fields)
        );

        assertThat(response.success()).isTrue();
        assertThat(response.respCode()).isEqualTo("0000");
        assertThat(response.respMsg()).isEqualTo("健康检查成功");
        assertThat(response.data()).isInstanceOf(Map.class);
        Map<String, Object> data = new LinkedHashMap<>();
        ((Map<?, ?>) response.data()).forEach((key, value) -> data.put(String.valueOf(key), value));
        assertThat(data)
            .containsEntry("webfe", "UP")
            .containsEntry("tuxedo", "UP")
            .containsEntry("oracle", "UP")
            .containsEntry("service", "SYSHEALTH")
            .containsEntry("checkTime", "2026-07-07 10:00:00");
    }
}
