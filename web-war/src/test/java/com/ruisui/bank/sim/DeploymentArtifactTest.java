package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactTest {
    private final Path root = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void oracleSchemaContainsOnlyPocBusinessTableAndIndexes() throws Exception {
        String schema = Files.readString(root.resolve("sql/schema.sql"));

        assertThat(schema).contains("CREATE TABLE T_CNAPS_BILL_POC");
        assertThat(schema).contains("CREATE UNIQUE INDEX UK_CNAPS_BILL_POC_SERIAL");
        assertThat(schema).contains("CREATE INDEX IDX_CNAPS_BILL_POC_QRY");
        assertThat(schema).doesNotContain("CREATE TABLE T_CNAPS_BILL_FLOW");
        assertThat(schema).doesNotContain("CREATE TABLE T_SYS_DICT");
        assertThat(schema).doesNotContain("CREATE TABLE T_BANK_INFO");
    }

    @Test
    void tuxedoArtifactsExposeRequiredServicesAndFields() throws Exception {
        String ubb = Files.readString(root.resolve("tuxedo/UBBCONFIG"));
        String fml = Files.readString(root.resolve("tuxedo-server/fml/cnaps_poc.fml32"));

        assertThat(ubb).contains("cnapspocsvr");
        assertThat(ubb).contains("SYSHEALTH", "DICTQRY", "BANKQRY", "CNAPS5701E", "CNAPS5701U", "CNAPS5701D");
        assertThat(ubb).contains("CNAPS4609Q", "CNAPS5702Q", "CNAPS5702I", "CNAPS5702A", "CNAPS5702R");
        assertThat(fml).contains("SYS_ID", "TXN_CODE", "REQ_ID", "OPERATOR_NO", "BRANCH_NO", "WORK_DATE");
        assertThat(fml).contains("BILL_ID", "SERIAL_NO", "BUSINESS_TYPE", "AMOUNT", "STATUS", "PAYEE_ACCT");
    }

    @Test
    void linuxConfigAndScriptsTargetPocAppHome() throws Exception {
        String env = Files.readString(root.resolve("conf/env.linux.sh"));
        String start = Files.readString(root.resolve("scripts/start.sh"));
        String stop = Files.readString(root.resolve("scripts/stop.sh"));
        String status = Files.readString(root.resolve("scripts/status.sh"));

        assertThat(env).contains("APP_HOME=/opt/ruisui-bank-sim");
        assertThat(env).contains("LD_LIBRARY_PATH");
        assertThat(start).contains("tmboot -y");
        assertThat(stop).contains("tmshutdown -y");
        assertThat(status).contains("tmadmin");
    }
}
