package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TuxedoRequestMapperTest {
    private final TuxedoRequestMapper mapper = new TuxedoRequestMapper();

    @Test
    void mapsHttpOperationToTraditionalTuxedoServiceName() {
        assertThat(mapper.serviceName("GET", "/api/health")).isEqualTo("SYSHEALTH");
        assertThat(mapper.serviceName("GET", "/api/dicts/BUSINESS_TYPE")).isEqualTo("DICTQRY");
        assertThat(mapper.serviceName("GET", "/api/banks")).isEqualTo("BANKQRY");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers")).isEqualTo("CNAPS5701E");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/query")).isEqualTo("CNAPS4609Q");
        assertThat(mapper.serviceName("GET", "/api/cnaps/vouchers/B202607087720002000")).isEqualTo("CNAPS5702I");
        assertThat(mapper.serviceName("PUT", "/api/cnaps/vouchers/B202607087720002000")).isEqualTo("CNAPS5701U");
        assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/B202607087720002000/delete")).isEqualTo("CNAPS5701D");
    }

    @Test
    void rejectsRetiredVoucherListGetMappings() {
        for (String path : List.of(
            "/api/cnaps/vouchers",
            "/api/cnaps/vouchers/query"
        )) {
            assertThatThrownBy(() -> mapper.serviceName("GET", path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported WebFE operation");
        }
    }

    @Test
    void mapsHeadersAndJsonBodyToCanonicalFmlFieldNames() {
        TuxedoRequest request = mapper.from(
            "REQ-1",
            "77210021",
            "772",
            Map.of(
                "amount", "1.00",
                "payeeAccountNo", "622200000000000001",
                "workDate", "2026-07-08",
                "payerAddress", "上海市浦东新区",
                "payeeAddress", "北京市朝阳区",
                "payerBankName", "中国示例银行上海分行"
            )
        );

        assertThat(request.fields())
            .containsEntry("REQUEST_ID", "REQ-1")
            .containsEntry("REQ_ID", "REQ-1")
            .containsEntry("OPERATOR_NO", "77210021")
            .containsEntry("BRANCH_NO", "772")
            .containsEntry("WORK_DATE", "2026-07-08")
            .containsEntry("AMOUNT", "1.00")
            .containsEntry("PAYEE_ACCT", "622200000000000001")
            .containsEntry("PAYER_ADDRESS", "上海市浦东新区")
            .containsEntry("PAYEE_ADDRESS", "北京市朝阳区")
            .containsEntry("PAYER_BANK_NAME", "中国示例银行上海分行");
    }

    @Test
    void keepsTrustedCommonFieldsWhilePreservingEndpointWorkDate() {
        TuxedoRequest request = mapper.from(
            "SERVER-REQ",
            "SERVER-OP",
            "SERVER-BRANCH",
            Map.of(
                "requestId", "CLIENT-REQ",
                "operatorNo", "CLIENT-OP",
                "branchNo", "CLIENT-BRANCH",
                "workDate", "2026-07-10"
            )
        );

        assertThat(request.fields())
            .containsEntry("REQUEST_ID", "SERVER-REQ")
            .containsEntry("REQ_ID", "SERVER-REQ")
            .containsEntry("OPERATOR_NO", "SERVER-OP")
            .containsEntry("BRANCH_NO", "SERVER-BRANCH")
            .containsEntry("WORK_DATE", "2026-07-10")
            .doesNotContainValue("CLIENT-REQ")
            .doesNotContainValue("CLIENT-OP")
            .doesNotContainValue("CLIENT-BRANCH");
    }

    @Test
    void mapsWorkDateRangeToCanonicalFmlFieldNames() {
        TuxedoRequest request = mapper.from(
            "SERVER-REQ",
            "SERVER-OP",
            "SERVER-BRANCH",
            Map.of("startWorkDate", "2026-07-10", "endWorkDate", "2026-07-12")
        );

        assertThat(request.fields())
            .containsEntry("START_WORK_DATE", "2026-07-10")
            .containsEntry("END_WORK_DATE", "2026-07-12");
    }

    @Test
    void exposesOnlyV03PaginationNames() {
        TuxedoRequest v03Request = mapper.from(
            "SERVER-REQ",
            "SERVER-OP",
            "SERVER-BRANCH",
            Map.of("pageNo", "2", "pageSize", "5")
        );
        TuxedoRequest legacyRequest = mapper.from(
            "SERVER-REQ",
            "SERVER-OP",
            "SERVER-BRANCH",
            Map.of("page", "9", "size", "99")
        );

        assertThat(v03Request.fields())
            .containsEntry("PAGE_NO", "2")
            .containsEntry("PAGE_SIZE", "5");
        assertThat(legacyRequest.fields()).doesNotContainKeys("PAGE_NO", "PAGE_SIZE");
    }
}
