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
}
