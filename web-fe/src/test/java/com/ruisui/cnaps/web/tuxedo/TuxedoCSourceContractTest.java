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
}
