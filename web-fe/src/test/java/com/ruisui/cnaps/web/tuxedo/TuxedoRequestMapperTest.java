package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TuxedoRequestMapperTest {
    private final TuxedoRequestMapper mapper = new TuxedoRequestMapper();

    @Test
    void mapsHttpOperationToTraditionalTuxedoServiceName() {
        assertThat(mapper.serviceName("GET", "/api/health")).isEqualTo("SYSHEALTH");
        assertThat(mapper.serviceName("GET", "/api/dicts/BUSINESS_TYPE")).isEqualTo("DICTQRY");
        assertThat(mapper.serviceName("GET", "/api/banks")).isEqualTo("BANKQRY");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers")).isEqualTo("CNAPS5701E");
        assertThat(mapper.serviceName("GET", "/api/cnaps/vouchers")).isEqualTo("CNAPS4609Q");
        assertThat(mapper.serviceName("GET", "/api/cnaps/vouchers/review-list")).isEqualTo("CNAPS5702Q");
        assertThat(mapper.serviceName("GET", "/api/cnaps/vouchers/B202607087720002000")).isEqualTo("CNAPS5702I");
        assertThat(mapper.serviceName("PUT", "/api/cnaps/vouchers/B202607087720002000")).isEqualTo("CNAPS5701U");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/B202607087720002000/delete")).isEqualTo("CNAPS5701D");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/B202607087720002000/review-pass")).isEqualTo("CNAPS5702A");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/B202607087720002000/review-return")).isEqualTo("CNAPS5702R");
    }

    @Test
    void mapsHeadersAndJsonBodyToCanonicalFmlFieldNames() {
        TuxedoRequest request = mapper.from(
            "REQ-1",
            "77210021",
            "772",
            "2026-07-08",
            Map.of("amount", "1.00", "payeeAccountNo", "622200000000000001")
        );

        assertThat(request.fields())
            .containsEntry("REQUEST_ID", "REQ-1")
            .containsEntry("REQ_ID", "REQ-1")
            .containsEntry("OPERATOR_NO", "77210021")
            .containsEntry("BRANCH_NO", "772")
            .containsEntry("WORK_DATE", "2026-07-08")
            .containsEntry("AMOUNT", "1.00")
            .containsEntry("PAYEE_ACCT", "622200000000000001");
    }

    @Test
    void keepsTrustedOperatorWhenBodyOrQueryContainsOperator() {
        TuxedoRequest request = mapper.from(
            "REQ",
            "SERVER-OP",
            "772",
            "2026-07-10",
            Map.of("operatorNo", "CLIENT-OP")
        );

        assertThat(request.fields())
            .containsEntry("OPERATOR_NO", "SERVER-OP")
            .doesNotContainValue("CLIENT-OP");
    }
}
