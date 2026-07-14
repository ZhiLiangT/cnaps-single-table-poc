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
            .contains("is_blank(raw_work_date)", "!cnaps_valid_work_date(raw_work_date)")
            .doesNotContain("row->work_date, sizeof(row->work_date), \"2026-07-09\"");
        assertThat(updateSource)
            .contains(
                "CNAPS_F_WORK_DATE, raw_work_date",
                "snprintf(row.work_date, sizeof(row.work_date), \"%s\", raw_work_date)"
            );
        assertThat(dbHelper)
            .contains("WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE)");
    }

    @Test
    void nativeLifecycleUsesReviewStatusesWithoutLegacyDraft() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
        String delete = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_delete.c"));
        String validation = Files.readString(root.resolve("tuxedo-server/src/common/validation_helper.c"));
        String status = Files.readString(root.resolve("tuxedo-server/include/cnaps_status.h"));

        assertThat(create).contains("CNAPS_STATUS_PENDING_REVIEW").doesNotContain("CNAPS_STATUS_DRAFT");
        assertThat(update).contains(
                "cnaps_status_can_edit", "CNAPS_STATUS_PENDING_REVIEW", "3003",
                "row.checker_no[0] = '\\0'", "row.checker_time[0] = '\\0'",
                "row.review_comment[0] = '\\0'", "row.reject_reason[0] = '\\0'"
            )
            .doesNotContain("CNAPS_STATUS_DRAFT");
        assertThat(delete).contains("cnaps_status_can_edit", "3003").doesNotContain("CNAPS_STATUS_DRAFT");
        assertThat(validation)
            .contains("strcmp(status, CNAPS_STATUS_PENDING_REVIEW) == 0")
            .contains("strcmp(status, CNAPS_STATUS_REJECTED) == 0")
            .contains("cnaps_status_can_review")
            .doesNotContain("CNAPS_STATUS_DRAFT");
        assertThat(status)
            .contains(
                "CNAPS_STATUS_PENDING_REVIEW", "CNAPS_STATUS_APPROVED",
                "CNAPS_STATUS_REJECTED", "CNAPS_STATUS_DELETED", "cnaps_status_can_review"
            )
            .doesNotContain("CNAPS_STATUS_DRAFT", "00_DRAFT");
        assertThat(root.resolve("tuxedo-server/src/services/cnaps_review.c")).doesNotExist();
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
    void nativeCreateUpdateAndDetailPersistPartyAddressFieldsWithoutAddingListOutput() throws Exception {
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_db.h"));
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));
        String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));

        assertThat(header).contains(
            "char payer_address[1025]", "char payee_address[1025]", "char payer_bank_name[513]"
        );
        assertThat(create).contains(
            "CNAPS_F_PAYER_ADDRESS", "CNAPS_F_PAYEE_ADDRESS", "CNAPS_F_PAYER_BANK_NAME",
            "cnaps_valid_optional_text"
        );
        assertThat(update).contains(
            "overlay_field(fbfr, CNAPS_F_PAYER_ADDRESS",
            "overlay_field(fbfr, CNAPS_F_PAYEE_ADDRESS",
            "overlay_field(fbfr, CNAPS_F_PAYER_BANK_NAME",
            "cnaps_valid_optional_text"
        );
        assertThat(db).contains(
            "PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME",
            ":payer_address", ":payee_address", ":payer_bank_name"
        );
        assertThat(query).contains("cnaps_put_voucher_detail_fields(fbfr, &row)");
        assertThat(fml)
            .contains("void cnaps_put_voucher_detail_fields")
            .doesNotContain(
                "PUT_STRING(CNAPS_F_PAYER_ADDRESS",
                "PUT_STRING(CNAPS_F_PAYEE_ADDRESS",
                "PUT_STRING(CNAPS_F_PAYER_BANK_NAME"
            );
    }

    @Test
    void nativeCreateKeepsTheFindKeySeparateFromTheHydratedRow() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));

        assertThat(create)
            .contains("char bill_id[33]", "db_find_voucher(bill_id, &row)")
            .doesNotContain("db_find_voucher(row.bill_id, &row)");
    }

    @Test
    void nativeCreateAndUpdateValidateBlankFieldsAndWorkDate() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));

        assertThat(create).contains(
            "is_blank(row->work_date)", "is_blank(row->business_type)",
            "is_blank(row->priority)", "is_blank(row->system_type)", "is_blank(row->account_part1)",
            "is_blank(row->payee_account_no)", "cnaps_valid_work_date(raw_work_date)", "2002"
        );
        assertThat(update).contains(
            "work_date_supplied", "cnaps_valid_work_date(raw_work_date)", "2002"
        );
    }

    @Test
    void nativeDetailFormatsDatabaseScaleAmountsWithoutLosingAnIntegerDigit() throws Exception {
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(db)
            .contains(
                "TO_CHAR(AMOUNT, 'FM9999999999999990D00')",
                "TO_CHAR(NVL(FEE_AMOUNT, 0), 'FM9999999999999990D00')"
            )
            .doesNotContain("FM999999999999990D00");
    }

    @Test
    void nativeVoucherQueriesCountThenFetchFullyDefinedOrderedPages() throws Exception {
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_db.h"));
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(header).contains(
            "const char *serial_no", "const char *voucher_no", "const char *payee_name",
            "const char *payee_account_no", "int include_deleted", "int page_no",
            "int page_size", "cnaps_voucher_row *rows", "int row_capacity", "int *total"
        );
        assertThat(db).contains(
            "SELECT COUNT(1) FROM T_CNAPS_BILL_POC",
            "(:serial_no IS NULL OR SERIAL_NO=:serial_no)",
            "(:voucher_no IS NULL OR VOUCHER_NO=:voucher_no)",
            "PAYEE_NAME LIKE '%' || :payee_name || '%'",
            "(:payee_account_no IS NULL OR PAYEE_ACCOUNT_NO=:payee_account_no)",
            "(:include_deleted=1 OR STATUS<>'40_DELETED')",
            "ORDER BY BILL_ID",
            "OFFSET :offset_rows ROWS FETCH NEXT :page_size ROWS ONLY",
            "define_voucher_row", "OCIStmtFetch2"
        );
        assertThat(db.indexOf("OCIStmtExecute(count)")).isLessThan(db.indexOf("OCIStmtExecute(query)"));
    }

    @Test
    void nativeVoucherQueryDoesNotRestoreANegativeRowCapacityAfterDisablingThePage() throws Exception {
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(db).contains(
            "if (rows == NULL || row_capacity <= 0) page_size = 0;",
            "if (row_capacity > 0 && page_size > row_capacity) page_size = row_capacity;"
        );
    }

    @Test
    void nativeQueryServicesUseSafeBoundsAndAlignedRepeatedOccurrences() throws Exception {
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_service.h"));
        String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));

        assertThat(header).contains("cnaps_put_string_occurrence", "cnaps_put_voucher_occurrence");
        assertThat(fml).contains(
            "Fchg32(fbfr, field_id, occurrence", "cnaps_put_voucher_occurrence",
            "CNAPS_F_BILL_ID", "CNAPS_F_VERSION_NO"
        );
        assertThat(query).contains(
            "CNAPS_QUERY_MAX_ROWS", "page_no = 1", "page_size = 10",
            "CNAPS_F_SERIAL_NO", "CNAPS_F_VOUCHER_NO", "CNAPS_F_PAYEE_NAME",
            "CNAPS_F_PAYEE_ACCT", "CNAPS_F_INCLUDE_DELETED",
            "cnaps_put_voucher_occurrence",
            "CNAPS_F_PAGE_NO", "CNAPS_F_PAGE_SIZE", "CNAPS_F_TOTAL_ELEMENTS"
        );
    }

    @Test
    void nativeReferenceQueriesReturnSupportedDictionariesAndFilteredBankPage() throws Exception {
        String dict = Files.readString(root.resolve("tuxedo-server/src/services/dict_query.c"));
        String bank = Files.readString(root.resolve("tuxedo-server/src/services/bank_query.c"));

        assertThat(dict).contains(
            "BUSINESS_TYPE", "PRIORITY", "FEE_CHARGE_MODE", "SEND_MODE", "DEBIT_MODE",
            "FAX_FLAG", "SYSTEM_TYPE", "02102", "NORM", "CNAPS", "2003",
            "cnaps_put_string_occurrence"
        );
        assertThat(bank).contains(
            "CNAPS_F_BANK_NO", "CNAPS_F_BANK_NAME", "CNAPS_F_CITY", "CNAPS_F_SYSTEM_TYPE",
            "CNAPS_F_KEYWORD", "strstr", "CNAPS_F_PAGE_NO", "CNAPS_F_PAGE_SIZE",
            "CNAPS_F_TOTAL_ELEMENTS"
        );
    }

    @Test
    void nativePagedQueriesReserveEnoughFmlResponseCapacityBeforeWritingRows() throws Exception {
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_service.h"));
        String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));
        String bank = Files.readString(root.resolve("tuxedo-server/src/services/bank_query.c"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));

        assertThat(header).contains(
            "FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size);"
        );
        assertThat(fml).contains(
            "Fsizeof32(fbfr)",
            "tprealloc(rqst->data, minimum_size)",
            "rqst->data = (char *)resized"
        );
        assertThat(bank).contains(
            "BANK_QUERY_RESPONSE_BUFFER_SIZE (16L * 1024L)",
            "cnaps_reserve_response_buffer(rqst, BANK_QUERY_RESPONSE_BUFFER_SIZE)",
            "response buffer allocation failed"
        );
        assertThat(query).contains(
            "CNAPS_QUERY_RESPONSE_BUFFER_SIZE (1024L * 1024L)",
            "cnaps_reserve_response_buffer(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE)",
            "response buffer allocation failed"
        );
        assertThat(query.split(
            "cnaps_reserve_response_buffer\\(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE\\)",
            -1
        )).hasSize(2);
        assertThat(bank.indexOf("cnaps_reserve_response_buffer"))
            .isLessThan(bank.indexOf("cnaps_put_long(fbfr, CNAPS_F_PAGE_NO"));
        assertThat(query.indexOf("cnaps_reserve_response_buffer"))
            .isLessThan(query.indexOf("cnaps_put_long(fbfr, CNAPS_F_PAGE_NO"));
    }

    @Test
    void nativeCreateAndUpdateValidateRequiredAndSuppliedDictionaryValues() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));

        assertThat(create)
            .contains(
                "row->business_type, sizeof(row->business_type), \"\"",
                "row->priority, sizeof(row->priority), \"\"",
                "row->system_type, sizeof(row->system_type), \"\"",
                "invalid_dictionary_fields(&row)", "2003",
                "02102", "NORM", "CNAPS", "debit_mode", "fee_charge_mode", "send_mode", "fax_flag"
            )
            .doesNotContain(
                "row->business_type, sizeof(row->business_type), \"02102\"",
                "row->priority, sizeof(row->priority), \"NORM\"",
                "row->system_type, sizeof(row->system_type), \"CNAPS\""
            );
        assertThat(update).contains(
            "business_type_supplied", "priority_supplied", "system_type_supplied",
            "debit_mode_supplied", "fee_charge_mode_supplied", "send_mode_supplied",
            "fax_flag_supplied", "invalid_dictionary_fields", "2003",
            "02102", "NORM", "CNAPS"
        );
    }

    @Test
    void nativeQueriesStrictlyValidateRangeDatesBeforeDatabaseAccess() throws Exception {
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_service.h"));
        String validation = Files.readString(root.resolve("tuxedo-server/src/common/validation_helper.c"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));

        assertThat(header).contains("int cnaps_valid_work_date(const char *value);");
        assertThat(validation).contains("cnaps_valid_work_date", "days_by_month", "year % 400");
        assertThat(query).contains(
            "raw_start_work_date[0] != '\\0' && !cnaps_valid_work_date(raw_start_work_date)",
            "raw_end_work_date[0] != '\\0' && !cnaps_valid_work_date(raw_end_work_date)",
            "cnaps_return_error(rqst, \"2002\", \"invalid start work date\")",
            "cnaps_return_error(rqst, \"2002\", \"invalid end work date\")"
        );
        assertThat(query.indexOf("cnaps_valid_work_date(raw_start_work_date)"))
            .isLessThan(query.indexOf("db_query_vouchers("));
        assertThat(query.indexOf("cnaps_valid_work_date(raw_end_work_date)"))
            .isLessThan(query.indexOf("db_query_vouchers("));
    }

    @Test
    void nativeVoucherQueriesSupportValidatedInclusiveWorkDateRanges() throws Exception {
        String fields = Files.readString(root.resolve("tuxedo-server/include/cnaps_fields.h"));
        String fml = Files.readString(root.resolve("tuxedo-server/fml/cnaps_poc.fml32"));
        String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_db.h"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));
        String headerQuery = header.substring(
            header.indexOf("int db_query_vouchers("),
            header.indexOf("int db_next_serial_no(")
        );
        String dbQuery = db.substring(
            db.indexOf("int db_query_vouchers("),
            db.indexOf("int db_next_serial_no(")
        );

        assertThat(fields).contains("CNAPS_F_START_WORK_DATE", "CNAPS_F_END_WORK_DATE");
        assertThat(fml).contains("START_WORK_DATE", "END_WORK_DATE");
        assertThat(headerQuery)
            .contains("const char *start_work_date", "const char *end_work_date")
            .doesNotContain("const char *work_date,");
        assertThat(query)
            .contains(
                "CNAPS_F_START_WORK_DATE", "CNAPS_F_END_WORK_DATE",
                "cnaps_valid_work_date(raw_start_work_date)",
                "cnaps_valid_work_date(raw_end_work_date)",
                "start work date is after end work date"
            )
            .doesNotContain(
                "get_field(fbfr, CNAPS_F_WORK_DATE",
                "work date cannot be combined with range",
                "char raw_work_date[513]",
                "char work_date[11]"
            );
        assertThat(countOccurrences(query, "get_field(fbfr, CNAPS_F_START_WORK_DATE")).isEqualTo(1);
        assertThat(countOccurrences(query, "get_field(fbfr, CNAPS_F_END_WORK_DATE")).isEqualTo(1);
        assertThat(db).contains(
            "(:start_work_date IS NULL OR WORK_DATE>=TO_DATE(:start_work_date, 'YYYY-MM-DD'))",
            "(:end_work_date IS NULL OR WORK_DATE<=TO_DATE(:end_work_date, 'YYYY-MM-DD')+(86399/86400))"
        ).doesNotContain(
            "WORK_DATE<TO_DATE(:end_work_date, 'YYYY-MM-DD')+1"
        );
        assertThat(dbQuery).doesNotContain(":work_date");
        assertThat(countOccurrences(db, "bind_text(stmt, \":start_work_date\", start_work_date)")).isEqualTo(2);
        assertThat(countOccurrences(db, "bind_text(stmt, \":end_work_date\", end_work_date)")).isEqualTo(2);
    }

    @Test
    void nativeBankKeywordMatchesNumber() throws Exception {
        String bank = Files.readString(root.resolve("tuxedo-server/src/services/bank_query.c"));

        assertThat(bank).contains(
            "strstr(bank_name, keyword) != NULL || strstr(bank_no, keyword) != NULL"
        );
    }

    @Test
    void nativeWorkDateValidationUsesCompleteUntruncatedInput() throws Exception {
        String validation = Files.readString(root.resolve("tuxedo-server/src/common/validation_helper.c"));
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
        String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));

        assertThat(validation).contains("year == 0", "strlen(value) != 10");
        assertThat(create).contains(
            "char raw_work_date[513]", "CNAPS_F_WORK_DATE, raw_work_date, sizeof(raw_work_date)",
            "!cnaps_valid_work_date(raw_work_date)", "row_from_create_request(fbfr, &row, raw_work_date)"
        );
        assertThat(create.indexOf("!cnaps_valid_work_date(raw_work_date)"))
            .isLessThan(create.indexOf("row_from_create_request(fbfr, &row, raw_work_date)"));
        assertThat(update).contains(
            "char raw_work_date[513]", "CNAPS_F_WORK_DATE, raw_work_date, sizeof(raw_work_date)",
            "!cnaps_valid_work_date(raw_work_date)",
            "snprintf(row.work_date, sizeof(row.work_date), \"%s\", raw_work_date)"
        ).doesNotContain(
            "overlay_field(fbfr, CNAPS_F_WORK_DATE, row.work_date, sizeof(row.work_date))"
        );
        assertThat(countOccurrences(query, "char raw_start_work_date[513]")).isEqualTo(1);
        assertThat(countOccurrences(query, "char raw_end_work_date[513]")).isEqualTo(1);
        assertThat(countOccurrences(query, "!cnaps_valid_work_date(raw_start_work_date)")).isEqualTo(1);
        assertThat(countOccurrences(query, "!cnaps_valid_work_date(raw_end_work_date)")).isEqualTo(1);
    }

    @Test
    void nativeBlankOptionalDictionaryValuesDefaultOrRemainUnchanged() throws Exception {
        String create = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_create.c"));
        String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));

        assertThat(create).contains(
            "is_blank(row->debit_mode)", "is_blank(row->fee_charge_mode)",
            "is_blank(row->send_mode)", "is_blank(row->fax_flag)", "apply_optional_defaults(&row)"
        );
        assertThat(update).contains(
            "overlay_optional_dictionary_field", "is_blank(value)",
            "debit_mode_supplied = overlay_optional_dictionary_field",
            "fee_charge_mode_supplied = overlay_optional_dictionary_field",
            "send_mode_supplied = overlay_optional_dictionary_field",
            "fax_flag_supplied = overlay_optional_dictionary_field"
        );
    }

    @Test
    void nativeVoucherQueryKeepsFilterAndFetchStatusesDistinct() throws Exception {
        String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));

        assertThat(db).contains(
            "const char *status_filter",
            "sword fetch_status;",
            "bind_text(stmt, \":status\", status_filter)",
            "fetch_status = OCIStmtFetch2",
            "fetch_status == OCI_NO_DATA",
            "oci_check(fetch_status, \"OCIStmtFetch2(query)\")"
        );
    }

    private int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
