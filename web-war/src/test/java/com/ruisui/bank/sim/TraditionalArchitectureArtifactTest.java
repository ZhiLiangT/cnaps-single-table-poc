package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TraditionalArchitectureArtifactTest {
    private final Path root = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void webFeIsClassicTomcatWarWithServletMappingsAndJspShell() throws Exception {
        Path webFe = root.resolve("web-fe");
        String pom = Files.readString(webFe.resolve("pom.xml"));
        String webXml = Files.readString(webFe.resolve("src/main/webapp/WEB-INF/web.xml"));

        assertThat(pom).contains("<packaging>war</packaging>");
        assertThat(pom).contains("javax.servlet-api", "javax.servlet.jsp-api");
        assertThat(pom).doesNotContain("spring-boot-starter");
        assertThat(webXml).contains(
            "/api/health",
            "/api/dicts/*",
            "/api/banks",
            "/api/cnaps/vouchers",
            "/api/cnaps/vouchers/*"
        );
        assertThat(webFe.resolve("src/main/webapp/index.jsp")).exists();
        assertThat(webFe.resolve("src/main/webapp/cnaps-create.jsp")).exists();
        assertThat(webFe.resolve("src/main/webapp/cnaps-query.jsp")).exists();
        assertThat(webFe.resolve("src/main/webapp/cnaps-review.jsp")).exists();
        assertThat(webFe.resolve("src/main/webapp/static/js/cnaps.js")).exists();
    }

    @Test
    void webFeHidesTuxedoTransportBehindAdapterInterface() throws Exception {
        Path source = root.resolve("web-fe/src/main/java/com/ruisui/cnaps/web");
        String client = Files.readString(source.resolve("tuxedo/TuxedoClient.java"));
        String joltClient = Files.readString(source.resolve("tuxedo/JoltTuxedoClient.java"));
        String voucherServlet = Files.readString(source.resolve("servlet/CnapsVoucherServlet.java"));

        assertThat(client).contains("TuxedoResponse call(String serviceName, TuxedoRequest request)");
        assertThat(joltClient).contains("class JoltTuxedoClient implements TuxedoClient");
        assertThat(voucherServlet).contains("TuxedoClient", "TuxedoRequestMapper");
        assertThat(voucherServlet).doesNotContain("CnapsBillPocRepository");
    }

    @Test
    void tuxedoServerHasAtmiServiceSkeletonAndBuildserverFlow() throws Exception {
        Path tuxedoServer = root.resolve("tuxedo-server");
        String entrypoints = Files.readString(tuxedoServer.resolve("src/cnapspocsvr.c"));
        String makefile = Files.readString(tuxedoServer.resolve("Makefile"));
        String dbHeader = Files.readString(tuxedoServer.resolve("include/cnaps_db.h"));
        String fml = Files.readString(tuxedoServer.resolve("fml/cnaps_poc.fml32"));

        assertThat(entrypoints).contains(
            "void SYSHEALTH(TPSVCINFO *rqst)",
            "void DICTQRY(TPSVCINFO *rqst)",
            "void BANKQRY(TPSVCINFO *rqst)",
            "void CNAPS5701E(TPSVCINFO *rqst)",
            "void CNAPS5701U(TPSVCINFO *rqst)",
            "void CNAPS5701D(TPSVCINFO *rqst)",
            "void CNAPS4609Q(TPSVCINFO *rqst)",
            "void CNAPS5702Q(TPSVCINFO *rqst)",
            "void CNAPS5702I(TPSVCINFO *rqst)",
            "void CNAPS5702A(TPSVCINFO *rqst)",
            "void CNAPS5702R(TPSVCINFO *rqst)"
        );
        assertThat(makefile).contains("buildserver", "-lclntsh", "bin/cnapspocsvr");
        assertThat(dbHeader).contains(
            "db_connect",
            "db_begin",
            "db_commit",
            "db_rollback",
            "db_insert_voucher",
            "db_update_voucher",
            "db_find_voucher",
            "db_query_vouchers",
            "db_next_serial_no"
        );
        assertThat(fml).contains("REQUEST_ID", "RESP_CODE", "RESP_MSG", "PAGE_NO", "PAGE_SIZE");
    }

    @Test
    void sqlplusAndLinuxLifecycleArtifactsAreSplitByRuntimeConcern() throws Exception {
        assertThat(root.resolve("sql/001_create_user.sql")).exists();
        assertThat(root.resolve("sql/010_create_tables.sql")).exists();
        assertThat(root.resolve("sql/020_create_indexes.sql")).exists();
        assertThat(root.resolve("sql/030_seed_reference_data.sql")).exists();
        assertThat(root.resolve("sql/090_drop_all.sql")).exists();

        String initDb = Files.readString(root.resolve("scripts/init-db.sh"));
        String buildC = Files.readString(root.resolve("scripts/build-c.sh"));
        String buildWeb = Files.readString(root.resolve("scripts/build-web.sh"));
        String loadTuxconfig = Files.readString(root.resolve("scripts/load-tuxconfig.sh"));
        String startTuxedo = Files.readString(root.resolve("scripts/start-tuxedo.sh"));
        String statusTuxedo = Files.readString(root.resolve("scripts/status-tuxedo.sh"));
        String smoke = Files.readString(root.resolve("scripts/smoke-test.sh"));

        assertThat(initDb).contains("set -eu", "sqlplus");
        assertThat(buildC).contains("set -eu", "make -C");
        assertThat(buildWeb).contains("set -eu", "mvn -f");
        assertThat(loadTuxconfig).contains("set -eu", "tmloadcf -y");
        assertThat(startTuxedo).contains("set -eu", "tmboot -y");
        assertThat(statusTuxedo).contains("set -eu", "tmadmin");
        assertThat(smoke).contains("set -eu", "/api/health", "/api/cnaps/vouchers");
    }
}
