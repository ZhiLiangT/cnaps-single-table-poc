package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TuxedoCSourceContractTest {
    private final Path root = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void dbHelperUsesOciInsteadOfStubbedReturnValues() throws Exception {
        String dbHelper = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(dbHelper)
            .contains("#include <oci.h>")
            .contains("OCIEnvCreate")
            .contains("OCILogon2")
            .contains("OCIStmtPrepare")
            .contains("OCITransCommit")
            .contains("INSERT INTO T_CNAPS_BILL_POC")
            .contains("SELECT")
            .doesNotContain("return 1;\n}");
    }

    @Test
    void fmlHelperWritesResponseCodeAndMessageIntoFml32Buffer() throws Exception {
        String serviceHeader = Files.readString(root.resolve("tuxedo-server/include/cnaps_service.h"));
        String fmlHelper = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));

        assertThat(serviceHeader)
            .contains("cnaps_get_string")
            .contains("cnaps_put_string")
            .contains("cnaps_return_response");
        assertThat(fmlHelper)
            .contains("Fget32")
            .contains("Fchg32")
            .contains("RESP_CODE")
            .contains("RESP_MSG");
    }

    @Test
    void healthServiceChecksOracleAndReturnsRealHealthFields() throws Exception {
        String health = Files.readString(root.resolve("tuxedo-server/src/services/sys_health.c"));

        assertThat(health)
            .contains("db_ping")
            .contains("ORACLE")
            .contains("TUXEDO")
            .contains("SYSHEALTH")
            .contains("cnaps_return_response");
    }

    @Test
    void nativeCreateRequiresWorkDateAndUpdatePersistsIt() throws Exception {
        String createSource = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String updateSource = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
        String dbHelper = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(createSource)
            .contains("row.work_date[0] == '\\0'")
            .doesNotContain("row->work_date, sizeof(row->work_date), \"2026-07-09\"");
        assertThat(updateSource)
            .contains("CNAPS_F_WORK_DATE, row.work_date");
        assertThat(dbHelper)
            .contains("WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE)");
    }

    @Test
    void nativeLifecycleChecksStateAndDoesNotRejectSameOperator() throws Exception {
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
        String delete = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_delete.c"));
        String review = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_review.c"));

        assertThat(update).contains("CNAPS_STATUS_PENDING_REVIEW", "CNAPS_STATUS_REJECTED", "3003");
        assertThat(delete).contains("CNAPS_STATUS_PENDING_REVIEW", "CNAPS_STATUS_REJECTED", "3003");
        assertThat(review)
            .contains("CNAPS_STATUS_PENDING_REVIEW", "3004")
            .doesNotContain("3005", "strcmp(row.operator_no, row.checker_no)");
    }

    @Test
    void nativeDetailAndFmlOutputCoverTheV03VoucherFields() throws Exception {
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));
        String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));

        assertThat(db).contains(
            "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3", "ACCOUNT_NAME", "PAYER_NAME",
            "RECEIVE_BANK_NO", "RECEIVE_BANK_NAME", "FEE_AMOUNT", "DELETE_TIME", "VERSION_NO"
        );
        assertThat(fml).contains(
            "CNAPS_F_ACCOUNT_PART1", "CNAPS_F_RECEIVE_BANK_NO", "CNAPS_F_FEE_AMOUNT",
            "CNAPS_F_DELETE_TIME", "CNAPS_F_VERSION_NO"
        );
    }

    @Test
    void nativeCreateKeepsTheFindKeySeparateFromTheHydratedRow() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));

        assertThat(create)
            .contains("char bill_id[33]", "db_find_voucher(bill_id, &row)")
            .doesNotContain("db_find_voucher(row.bill_id, &row)");
    }
}
