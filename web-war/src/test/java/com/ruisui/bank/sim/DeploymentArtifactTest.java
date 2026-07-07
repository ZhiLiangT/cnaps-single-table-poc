package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;

import com.ruisui.bank.sim.persistence.CnapsBillPoc;

import jakarta.persistence.Column;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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
    void oracleSchemaAndEntityUseTheSameCanonicalColumnsAndLengths() throws Exception {
        String schema = Files.readString(root.resolve("sql/schema.sql"));

        assertThat(columnName("billId")).isEqualTo("BILL_ID");
        assertThat(columnName("accountPart1")).isEqualTo("ACCOUNT_PART1");
        assertThat(columnName("accountPart2")).isEqualTo("ACCOUNT_PART2");
        assertThat(columnName("accountPart3")).isEqualTo("ACCOUNT_PART3");
        assertThat(columnName("lastActionAt")).isEqualTo("LAST_ACTION_TIME");

        assertThat(column("billId").length()).isEqualTo(32);
        assertThat(column("operatorNo").length()).isEqualTo(16);
        assertThat(column("branchNo").length()).isEqualTo(12);
        assertThat(column("payeeAccountNo").nullable()).isFalse();
        assertThat(column("payeeName").nullable()).isFalse();
        assertThat(column("amount").precision()).isEqualTo(18);
        assertThat(column("amount").scale()).isEqualTo(2);
        assertThat(column("faxFlag").length()).isEqualTo(1);
        assertThat(column("rejectReason").length()).isEqualTo(200);
        assertThat(column("deleteReason").length()).isEqualTo(200);

        assertThat(schema).contains("BILL_ID VARCHAR2(32) PRIMARY KEY");
        assertThat(schema).contains("OPERATOR_NO VARCHAR2(16) NOT NULL");
        assertThat(schema).contains("BRANCH_NO VARCHAR2(12) NOT NULL");
        assertThat(schema).contains("PAYEE_ACCOUNT_NO VARCHAR2(64) NOT NULL");
        assertThat(schema).contains("PAYEE_NAME VARCHAR2(128) NOT NULL");
        assertThat(schema).contains("AMOUNT NUMBER(18,2) NOT NULL");
        assertThat(schema).contains("FAX_FLAG VARCHAR2(1)");
        assertThat(schema).contains("REJECT_REASON VARCHAR2(200)");
        assertThat(schema).contains("DELETE_REASON VARCHAR2(200)");
        assertThat(schema).contains("LAST_ACTION_TIME TIMESTAMP");
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
    void dictionaryAndBankArtifactsContainPrdValues() throws Exception {
        Properties dicts = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("conf/dicts.properties"), StandardCharsets.UTF_8)) {
            dicts.load(reader);
        }

        Properties banks = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("conf/banks.properties"), StandardCharsets.UTF_8)) {
            banks.load(reader);
        }

        assertThat(dicts.getProperty("BUSINESS_TYPE.02102")).isEqualTo("普通汇兑");
        assertThat(dicts.getProperty("PRIORITY.NORM")).isEqualTo("普通");
        assertThat(dicts.getProperty("FEE_CHARGE_MODE.1")).isEqualTo("同城收费");
        assertThat(dicts.getProperty("SEND_MODE.0")).isEqualTo("柜面");
        assertThat(dicts.getProperty("DEBIT_MODE.1")).isEqualTo("扣收");
        assertThat(dicts.getProperty("FAX_FLAG.0")).isEqualTo("否");
        assertThat(dicts.getProperty("FAX_FLAG.1")).isEqualTo("是");
        assertThat(dicts.getProperty("SYSTEM_TYPE.CNAPS")).isEqualTo("CNAPS");
        assertThat(banks.getProperty("BANK.102290000002")).isEqualTo("接收行名称");
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

    private static Column column(String fieldName) throws NoSuchFieldException {
        return CnapsBillPoc.class.getDeclaredField(fieldName).getAnnotation(Column.class);
    }

    private static String columnName(String fieldName) throws NoSuchFieldException {
        return column(fieldName).name();
    }
}
