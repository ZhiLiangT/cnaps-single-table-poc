package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactTest {
    private static final String[] EXPORTED_SERVICES = {
        "SYSHEALTH", "DICTQRY", "BANKQRY", "CNAPS5701E", "CNAPS5701U", "CNAPS5701D",
        "CNAPS4609Q", "CNAPS5702Q", "CNAPS5702I", "CNAPS5702A", "CNAPS5702R"
    };
    private static final String[] VOUCHER_RECORD_FIELDS = {
        "BILL_ID", "WORK_DATE", "BRANCH_NO", "OPERATOR_NO", "SERIAL_NO", "BUSINESS_TYPE",
        "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3", "ACCOUNT_NAME", "PAYER_NAME",
        "PAYEE_ACCT", "PAYEE_NAME", "PRIORITY", "RECEIVE_BANK_NO", "RECEIVE_BANK_NAME",
        "SYSTEM_TYPE", "AMOUNT", "DEBIT_MODE", "FEE_AMOUNT", "FEE_CHARGE_MODE", "SEND_MODE",
        "FAX_FLAG", "VOUCHER_NO", "REMARK", "STATUS", "CHECKER_NO", "CHECKER_TIME",
        "REJECT_REASON", "REVIEW_COMMENT", "DELETE_REASON", "DELETE_OPERATOR_NO", "DELETE_TIME",
        "LAST_ACTION", "LAST_OPERATOR_NO", "LAST_REQUEST_ID", "LAST_ACTION_TIME", "CREATED_AT",
        "UPDATED_AT", "VERSION_NO"
    };
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
    void repeatedJoltFieldsHaveUnlimitedOccurrenceCountsAndCorrectDirections() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));

        assertRepeatedParam(metadata, "DICTQRY", "DICT_TYPE", "string", "inout");
        assertRepeatedParam(metadata, "DICTQRY", "DICT_CODE", "string", "out");
        assertRepeatedParam(metadata, "DICTQRY", "DICT_NAME", "string", "out");
        assertRepeatedParam(metadata, "DICTQRY", "SORT_NO", "long", "out");

        assertRepeatedParam(metadata, "BANKQRY", "BANK_NO", "string", "inout");
        assertRepeatedParam(metadata, "BANKQRY", "BANK_NAME", "string", "out");
        assertRepeatedParam(metadata, "BANKQRY", "CITY", "string", "inout");
        assertRepeatedParam(metadata, "BANKQRY", "SYSTEM_TYPE", "string", "inout");

        for (String field : VOUCHER_RECORD_FIELDS) {
            String type = "VERSION_NO".equals(field) ? "long" : "string";
            String generalAccess = switch (field) {
                case "WORK_DATE", "BRANCH_NO", "STATUS", "SERIAL_NO", "VOUCHER_NO", "PAYEE_NAME", "PAYEE_ACCT" -> "inout";
                default -> "out";
            };
            String reviewAccess = switch (field) {
                case "WORK_DATE", "BRANCH_NO", "SERIAL_NO" -> "inout";
                default -> "out";
            };
            assertRepeatedParam(metadata, "CNAPS4609Q", field, type, generalAccess);
            assertRepeatedParam(metadata, "CNAPS5702Q", field, type, reviewAccess);
        }
    }

    @Test
    void singleVoucherServicesTransportCompleteInputsAndVoucherOutputsWithoutOccurrences() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));
        String[] mutableFields = {
            "WORK_DATE", "BUSINESS_TYPE", "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3",
            "ACCOUNT_NAME", "PAYER_NAME", "PAYEE_ACCT", "PAYEE_NAME", "PRIORITY",
            "RECEIVE_BANK_NO", "RECEIVE_BANK_NAME", "SYSTEM_TYPE", "AMOUNT", "DEBIT_MODE",
            "FEE_AMOUNT", "FEE_CHARGE_MODE", "SEND_MODE", "FAX_FLAG", "VOUCHER_NO", "REMARK"
        };

        for (String service : new String[] {
            "CNAPS5701E", "CNAPS5701U", "CNAPS5701D", "CNAPS5702I", "CNAPS5702A", "CNAPS5702R"
        }) {
            assertScalarParam(metadata, service, "REQUEST_ID", "string", "inout");
            assertScalarParam(metadata, service, "REQ_ID", "string", "inout");
            assertScalarParam(metadata, service, "OPERATOR_NO", "string", "inout");
            assertScalarParam(metadata, service, "BRANCH_NO", "string", "inout");
            for (String field : VOUCHER_RECORD_FIELDS) {
                String type = "VERSION_NO".equals(field) ? "long" : "string";
                String access = expectedSingleVoucherAccess(service, field, mutableFields);
                assertScalarParam(metadata, service, field, type, access);
            }
        }

        assertScalarParam(metadata, "CNAPS5701D", "DELETE_REASON", "string", "inout");
        assertScalarParam(metadata, "CNAPS5702A", "REVIEW_COMMENT", "string", "inout");
        assertScalarParam(metadata, "CNAPS5702R", "REJECT_REASON", "string", "inout");
        assertScalarParam(metadata, "CNAPS5702R", "REVIEW_COMMENT", "string", "inout");
    }

    @Test
    void pageRequestAndEnvelopeMetadataRemainScalarAndErrorsUseOuterr() throws Exception {
        String metadata = Files.readString(root.resolve("tuxedo/jolt/cnaps_services.bulk"));

        assertScalarParam(metadata, "DICTQRY", "REQUEST_ID", "string", "inout");
        assertScalarParam(metadata, "BANKQRY", "KEYWORD", "string", "in");
        assertScalarParam(metadata, "BANKQRY", "PAGE_NO", "long", "inout");
        assertScalarParam(metadata, "BANKQRY", "PAGE_SIZE", "long", "inout");
        assertScalarParam(metadata, "BANKQRY", "TOTAL_ELEMENTS", "long", "out");
        assertScalarParam(metadata, "CNAPS4609Q", "INCLUDE_DELETED", "string", "in");
        assertScalarParam(metadata, "CNAPS4609Q", "PAGE_NO", "long", "inout");
        assertScalarParam(metadata, "CNAPS4609Q", "PAGE_SIZE", "long", "inout");
        assertScalarParam(metadata, "CNAPS4609Q", "TOTAL_ELEMENTS", "long", "out");
        assertScalarParam(metadata, "CNAPS5702Q", "PAGE_NO", "long", "inout");
        assertScalarParam(metadata, "CNAPS5702Q", "PAGE_SIZE", "long", "inout");
        assertScalarParam(metadata, "CNAPS5702Q", "TOTAL_ELEMENTS", "long", "out");

        for (String service : EXPORTED_SERVICES) {
            assertScalarParam(metadata, service, "RESP_CODE", "string", "outerr");
            assertScalarParam(metadata, service, "RESP_MSG", "string", "outerr");
        }
    }

    @Test
    void operationalScriptsCoverPreflightAndJoltMetadataLoad() {
        assertThat(root.resolve("scripts/preflight.sh")).exists();
        assertThat(root.resolve("scripts/load-jolt-metadata.sh")).exists();
        assertThat(root.resolve("scripts/install-tuxedo.sh")).exists();
        assertThat(root.resolve("scripts/configure-tomcat.sh")).exists();
    }

    @Test
    void schemaAndInitScriptProvideRepeatablePartyAddressMigration() throws Exception {
        String createTable = Files.readString(root.resolve("sql/010_create_tables.sql"));
        String schema = Files.readString(root.resolve("sql/schema.sql"));
        Path migrationPath = root.resolve("sql/040_add_party_address_bank_fields.sql");
        String initDb = Files.readString(root.resolve("scripts/init-db.sh"));

        assertThat(createTable).contains(
            "PAYER_ADDRESS VARCHAR2(256 CHAR)",
            "PAYEE_ADDRESS VARCHAR2(256 CHAR)",
            "PAYER_BANK_NAME VARCHAR2(128 CHAR)"
        );
        assertThat(schema).contains(
            "PAYER_ADDRESS VARCHAR2(256 CHAR)",
            "PAYEE_ADDRESS VARCHAR2(256 CHAR)",
            "PAYER_BANK_NAME VARCHAR2(128 CHAR)"
        );
        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);
        assertThat(countOccurrences(migration, "USER_TAB_COLUMNS")).isEqualTo(3);
        assertThat(countOccurrences(migration, "ALTER TABLE T_CNAPS_BILL_POC ADD")).isEqualTo(3);
        assertThat(migration).contains("PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME");
        assertThat(initDb).contains("040_add_party_address_bank_fields.sql");
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
    void frontendApiDocumentsTheApprovedHeaderlessV03Contract() throws Exception {
        String api = Files.readString(root.resolve("docs/cnaps-frontend-api.md"));

        assertThat(api).contains(
            "`respCode`",
            "`respMsg`",
            "`data`",
            "创建时必填",
            "修改时可选",
            "`pageNo`",
            "`pageSize`",
            "不校验复核人与录入人是否相同"
        ).doesNotContain(
            "| `success`",
            "| `page` |",
            "| `size` |",
            "不能复核本人录入单据",
            "`3005`",
            "-H \"requestId:",
            "-H \"operatorNo:",
            "-H \"branchNo:",
            "-H \"workDate:"
        );

        assertThat(api).contains(
            "| `GET /api/health` | `SYSHEALTH` |",
            "| `GET /api/dicts/{dictType}` | `DICTQRY` |",
            "| `GET /api/banks` | `BANKQRY` |",
            "| `POST /api/cnaps/vouchers` | `CNAPS5701E` |",
            "| `PUT /api/cnaps/vouchers/{billId}` | `CNAPS5701U` |",
            "| `POST /api/cnaps/vouchers/{billId}/delete` | `CNAPS5701D` |",
            "| `GET /api/cnaps/vouchers` | `CNAPS4609Q` |",
            "| `GET /api/cnaps/vouchers/review-list` | `CNAPS5702Q` |",
            "| `GET /api/cnaps/vouchers/{billId}` | `CNAPS5702I` |",
            "| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` |",
            "| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` |"
        );

        assertThat(Pattern.compile("(?m)^### 4\\.\\d+ ").matcher(api).results()).hasSize(11);
        assertThat(countOccurrences(api, "-H \"Content-Type: application/json; charset=UTF-8\"")).isEqualTo(5);

        String errorCodeTable = api.substring(api.indexOf("## 6. 错误码"), api.indexOf("失败示例："));
        List<String> activeErrorCodes = Pattern.compile("(?m)^\\| `(\\d{4})` \\|")
            .matcher(errorCodeTable)
            .results()
            .map(result -> result.group(1))
            .toList();
        assertThat(activeErrorCodes).containsExactly(
            "0000", "2001", "2002", "2003", "3001", "3003", "3004",
            "4001", "4002", "4003", "9999"
        );
    }

    @Test
    void smokeCreatePayloadContainsEveryMandatoryVoucherField() throws Exception {
        String smoke = Files.readString(root.resolve("scripts/smoke-test.sh"));
        int createStart = smoke.indexOf("create_response=$(curl");
        int createEnd = smoke.indexOf("echo \"$create_response\"", createStart);
        String createRequest = smoke.substring(createStart, createEnd);

        assertThat(createRequest).contains(
            "\\\"workDate\\\"",
            "\\\"businessType\\\"",
            "\\\"accountPart1\\\"",
            "\\\"accountPart2\\\"",
            "\\\"accountPart3\\\"",
            "\\\"payeeAccountNo\\\"",
            "\\\"payeeName\\\"",
            "\\\"priority\\\"",
            "\\\"systemType\\\"",
            "\\\"amount\\\""
        ).doesNotContain(
            "-H \"requestId:",
            "-H \"operatorNo:",
            "-H \"branchNo:",
            "-H \"workDate:"
        );
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

    private int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private String serviceMetadata(String metadata, String serviceName) {
        int start = metadata.indexOf("service=" + serviceName);
        int end = metadata.indexOf("service=", start + 1);
        return normalizeLineEndings(metadata.substring(start, end < 0 ? metadata.length() : end));
    }

    private String paramMetadata(String metadata, String serviceName, String paramName) {
        String service = serviceMetadata(metadata, serviceName);
        int start = service.indexOf("param=" + paramName + "\n");
        assertThat(start).as(serviceName + " " + paramName).isGreaterThanOrEqualTo(0);
        int end = service.indexOf("param=", start + 1);
        return service.substring(start, end < 0 ? service.length() : end);
    }

    private void assertRepeatedParam(
        String metadata,
        String serviceName,
        String paramName,
        String type,
        String access
    ) {
        assertThat(paramMetadata(metadata, serviceName, paramName))
            .contains("type=" + type + "\n", "access=" + access + "\n", "count=0\n");
    }

    private void assertScalarParam(
        String metadata,
        String serviceName,
        String paramName,
        String type,
        String access
    ) {
        assertThat(paramMetadata(metadata, serviceName, paramName))
            .contains("type=" + type + "\n", "access=" + access + "\n")
            .doesNotContain("count=");
    }

    private String expectedSingleVoucherAccess(String service, String field, String[] mutableFields) {
        if ("BILL_ID".equals(field)) {
            return "CNAPS5701E".equals(service) ? "out" : "inout";
        }
        if ("OPERATOR_NO".equals(field) || "BRANCH_NO".equals(field)) {
            return "inout";
        }
        if (("CNAPS5701E".equals(service) || "CNAPS5701U".equals(service))
            && List.of(mutableFields).contains(field)) {
            return "inout";
        }
        if ("CNAPS5701D".equals(service) && "DELETE_REASON".equals(field)) {
            return "inout";
        }
        if (("CNAPS5702A".equals(service) || "CNAPS5702R".equals(service))
            && "REVIEW_COMMENT".equals(field)) {
            return "inout";
        }
        if ("CNAPS5702R".equals(service) && "REJECT_REASON".equals(field)) {
            return "inout";
        }
        return "out";
    }
}
