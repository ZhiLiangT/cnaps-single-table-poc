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
        String updateMetadata = metadata.substring(updateStart, updateEnd);

        assertThat(updateMetadata)
            .contains("param=WORK_DATE")
            .contains("type=string")
            .contains("access=inout");
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
                "POC_BRANCH_NO",
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
                "修改时可选")
            .doesNotContain(
                "### 1.4 公共请求头",
                "| `operatorNo`",
                "-H \"requestId:",
                "-H \"operatorNo:",
                "-H \"branchNo:",
                "-H \"workDate:");
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
}
