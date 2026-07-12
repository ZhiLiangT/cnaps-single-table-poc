package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactTest {
    private final Path root = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void ubbconfigExposesBusinessServerAndJoltListener() throws Exception {
        String ubb = Files.readString(root.resolve("tuxedo/UBBCONFIG"));

        assertThat(ubb)
            .contains("APPDIR")
            .contains("MAXWSCLIENTS")
            .contains("cnapspocsvr")
            .contains("JSL")
            .contains("//127.0.0.1:8000")
            .contains("SYSHEALTH")
            .contains("CNAPS5701E")
            .contains("CNAPS5702R");
    }

    @Test
    void joltMetadataDefinesAllExportedServicesAndCoreFmlFields() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));

        assertThat(metadata)
            .contains("SYSHEALTH")
            .contains("DICTQRY")
            .contains("BANKQRY")
            .contains("CNAPS5701E")
            .contains("CNAPS5701U")
            .contains("CNAPS5701D")
            .contains("CNAPS4609Q")
            .contains("CNAPS5702Q")
            .contains("CNAPS5702I")
            .contains("CNAPS5702A")
            .contains("CNAPS5702R")
            .contains("REQUEST_ID")
            .contains("RESP_CODE")
            .contains("RESP_MSG")
            .contains("BILL_ID")
            .contains("PAYEE_ACCT")
            .contains("TOTAL_ELEMENTS");
    }

    @Test
    void updateServiceMetadataAcceptsWorkDate() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));
        int updateStart = metadata.indexOf("service=CNAPS5701U");
        int updateEnd = metadata.indexOf("service=", updateStart + 1);
        String updateMetadata = normalizeLineEndings(metadata.substring(updateStart, updateEnd));

        assertThat(updateMetadata).contains("param=WORK_DATE\ntype=string\naccess=inout");
    }

    @Test
    void generalQueryMetadataCoversAllFiltersAndRepeatedVoucherFields() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));
        String queryMetadata = serviceMetadata(metadata, "CNAPS4609Q");

        assertThat(queryMetadata).contains(
            "param=WORK_DATE", "param=BRANCH_NO", "param=STATUS", "param=SERIAL_NO",
            "param=VOUCHER_NO", "param=PAYEE_NAME", "param=PAYEE_ACCT",
            "param=INCLUDE_DELETED", "param=PAGE_NO", "param=PAGE_SIZE",
            "param=TOTAL_ELEMENTS", "param=BILL_ID", "param=OPERATOR_NO",
            "param=BUSINESS_TYPE", "param=ACCOUNT_PART1", "param=ACCOUNT_PART2",
            "param=ACCOUNT_PART3", "param=ACCOUNT_NAME", "param=PAYER_NAME",
            "param=PRIORITY", "param=RECEIVE_BANK_NO", "param=RECEIVE_BANK_NAME",
            "param=SYSTEM_TYPE", "param=AMOUNT", "param=DEBIT_MODE", "param=FEE_AMOUNT",
            "param=FEE_CHARGE_MODE", "param=SEND_MODE", "param=FAX_FLAG",
            "param=REMARK", "param=CHECKER_NO", "param=CHECKER_TIME",
            "param=REJECT_REASON", "param=REVIEW_COMMENT", "param=DELETE_REASON",
            "param=DELETE_OPERATOR_NO", "param=DELETE_TIME", "param=LAST_ACTION",
            "param=LAST_OPERATOR_NO", "param=LAST_REQUEST_ID", "param=LAST_ACTION_TIME",
            "param=CREATED_AT", "param=UPDATED_AT", "param=VERSION_NO"
        );
    }

    @Test
    void referenceMetadataExposesDictionaryArraysAndBankPages() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));

        assertThat(serviceMetadata(metadata, "DICTQRY")).contains(
            "param=DICT_TYPE", "param=DICT_CODE", "param=DICT_NAME", "param=SORT_NO"
        );
        assertThat(serviceMetadata(metadata, "BANKQRY")).contains(
            "param=BANK_NO", "param=KEYWORD", "param=CITY", "param=SYSTEM_TYPE",
            "param=PAGE_NO", "param=PAGE_SIZE", "param=TOTAL_ELEMENTS", "param=BANK_NAME"
        );
        assertThat(Files.readString(root.resolve("tuxedo-server/fml/cnaps_poc.fml32")))
            .contains("CITY", "KEYWORD");
    }

    @Test
    void operationalScriptsCoverPreflightAndJoltMetadataLoad() {
        assertThat(root.resolve("scripts/preflight.sh")).exists();
        assertThat(root.resolve("scripts/load-jolt-metadata.sh")).exists();
        assertThat(root.resolve("scripts/install-tuxedo.sh")).exists();
        assertThat(root.resolve("scripts/configure-tomcat.sh")).exists();
    }

    @Test
    void tuxedo22cLocalJoltConfigurationAllowsNonTlsLoopbackForPoc() throws Exception {
        String tuxedoEnv = Files.readString(root.resolve("conf/tuxedo.env"));
        String tomcatConfig = Files.readString(root.resolve("scripts/configure-tomcat.sh"));

        assertThat(tuxedoEnv)
            .contains("TM_SECURITY_CONFIG")
            .contains("TM_ALLOW_NOTLS");
        assertThat(tomcatConfig)
            .contains("-DTM_ALLOW_NOTLS=");
    }

    @Test
    void deploymentArtifactsConfigureTheServerOperatorAndBranch() throws Exception {
        assertThat(Files.readString(root.resolve("conf/app.properties")))
            .contains(
                "webfe.poc.operatorNo=77210021",
                "webfe.poc.branchNo=772");
        assertThat(Files.readString(root.resolve("scripts/configure-tomcat.sh")))
            .contains(
                "POC_OPERATOR_NO",
                "POC_BRANCH_NO=${POC_BRANCH_NO:-772}",
                "echo \"POC_BRANCH_NO=\\\"$POC_BRANCH_NO\\\"\"",
                "-Dwebfe.poc.operatorNo=",
                "-Dwebfe.poc.branchNo=");
    }

    @Test
    void frontendApiDocumentsTheServerContextAndWorkDateContract() throws Exception {
        assertThat(Files.readString(root.resolve("docs/cnaps-frontend-api.md")))
            .contains(
                "POC_OPERATOR_NO",
                "POC_BRANCH_NO",
                "webfe.poc.operatorNo",
                "workDate",
                "创建时必填",
                "修改时可选",
                "工作日期过滤；未传时默认当前日期。",
                "修改 `workDate` 不会重新生成 `billId` 或 `serialNo`，两者保持不变。")
            .doesNotContain(
                "### 1.4 公共请求头",
                "| `operatorNo`",
                "-H \"requestId:",
                "-H \"operatorNo:",
                "-H \"branchNo:",
                "-H \"workDate:");
    }

    @Test
    void createPageSubmitsWorkDateWithoutBusinessHeaders() throws Exception {
        String page = Files.readString(root.resolve("web-fe/src/main/webapp/cnaps-create.jsp"));
        String script = Files.readString(root.resolve("web-fe/src/main/webapp/static/js/cnaps.js"));

        assertThat(page).contains("name=\"workDate\"");
        assertThat(script)
            .contains("\"Content-Type\": \"application/json; charset=UTF-8\"")
            .doesNotContain("requestId:", "operatorNo:", "branchNo:", "workDate:");
    }

    @Test
    void operationsGuideDocumentsAutomationAndCredentialSafety() throws Exception {
        Path operationsGuide = root.resolve("docs/cnaps-operations.md");

        assertThat(operationsGuide).exists();
        String operations = Files.readString(operationsGuide);
        assertThat(operations).contains(
            "./scripts/rebuild-deploy.sh",
            "./scripts/up.sh",
            "./scripts/down.sh --all",
            "./scripts/cnapsctl.sh install-autostart",
            "不要提交、复制或输出 conf/db.env",
            "192.168.84.134",
            "/home/tian/cnaps-single-table-poc");
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    private String serviceMetadata(String metadata, String serviceName) {
        int start = metadata.indexOf("service=" + serviceName);
        int end = metadata.indexOf("service=", start + 1);
        return normalizeLineEndings(metadata.substring(start, end < 0 ? metadata.length() : end));
    }
}
