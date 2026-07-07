package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import com.ruisui.bank.sim.api.dto.DeleteRequest;
import com.ruisui.bank.sim.api.dto.ReviewPassRequest;
import com.ruisui.bank.sim.api.dto.ReviewReturnRequest;
import com.ruisui.bank.sim.api.dto.VoucherCreateRequest;
import com.ruisui.bank.sim.api.dto.VoucherResponse;
import com.ruisui.bank.sim.domain.BusinessException;
import com.ruisui.bank.sim.domain.ErrorCode;
import com.ruisui.bank.sim.domain.LastAction;
import com.ruisui.bank.sim.domain.VoucherStatus;
import com.ruisui.bank.sim.persistence.CnapsBillPoc;
import com.ruisui.bank.sim.persistence.CnapsBillPocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class CnapsVoucherService {
    private static final DateTimeFormatter BILL_ID_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<String> SUPPORTED_BUSINESS_TYPES = List.of("02102");
    private static final List<String> SUPPORTED_PRIORITIES = List.of("NORM");
    private static final List<String> SUPPORTED_FEE_CHARGE_MODES = List.of("1");
    private static final List<String> SUPPORTED_SEND_MODES = List.of("0");
    private static final List<String> SUPPORTED_DEBIT_MODES = List.of("1");
    private static final List<String> SUPPORTED_FAX_FLAGS = List.of("0", "1");
    private static final List<String> SUPPORTED_SYSTEM_TYPES = List.of("CNAPS");

    private final CnapsBillPocRepository repository;
    private final VoucherMapper mapper;

    public CnapsVoucherService(CnapsBillPocRepository repository, VoucherMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public VoucherResponse create(HeaderContext context, VoucherCreateRequest request) {
        validateCreate(request);

        String maxSerialNo = repository.findMaxSerialNo(context.workDate(), context.branchNo());
        String serialNo = nextSerialNo(maxSerialNo);
        String billId = buildBillId(context, serialNo);
        BigDecimal amount = parseAmount(request.amount());
        BigDecimal feeAmount = parseOptionalAmount(request.feeAmount());

        CnapsBillPoc entity = mapper.toEntity(context, request, billId, serialNo, amount, feeAmount);
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> query(HeaderContext context, String status, String operatorNo, String serialNo, int page, int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.ASC, "serialNo"));
        Page<CnapsBillPoc> result = repository.findByWorkDateAndBranchNoWithFilters(
            context.workDate(),
            context.branchNo(),
            status,
            operatorNo,
            serialNo,
            pageable
        );
        return mapper.toResponsePage(result);
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> reviewList(HeaderContext context, int page, int size) {
        return query(context, com.ruisui.bank.sim.domain.VoucherStatus.PENDING_REVIEW.code(), null, null, page, size);
    }

    @Transactional(readOnly = true)
    public VoucherResponse detail(HeaderContext context, String billId) {
        CnapsBillPoc entity = repository.findByBillId(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "voucher not found"));
        if (!context.branchNo().equals(entity.getBranchNo()) || !context.workDate().equals(entity.getWorkDate())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "voucher not found");
        }
        return mapper.toResponse(entity);
    }

    public VoucherResponse update(HeaderContext context, String billId, VoucherCreateRequest request) {
        validateCreate(request);
        CnapsBillPoc entity = loadLifecycleEntity(context, billId);
        requireEditable(entity);

        mapper.applySubmissionFields(entity, request, parseAmount(request.amount()), parseOptionalAmount(request.feeAmount()));
        entity.setStatus(VoucherStatus.PENDING_REVIEW.code());
        entity.setLastAction(LastAction.UPDATE.code());
        entity.setVersionNo(nextVersionNo(entity));
        entity.setRejectReason(null);
        entity.setReviewComment(null);
        entity.setCheckerNo(null);
        entity.setCheckerTime(null);
        entity.setDeleteReason(null);
        entity.setDeleteOperatorNo(null);
        entity.setDeleteTime(null);
        touch(entity);
        return mapper.toResponse(repository.save(entity));
    }

    public VoucherResponse delete(HeaderContext context, String billId, DeleteRequest request) {
        CnapsBillPoc entity = loadLifecycleEntity(context, billId);
        requireEditable(entity);

        entity.setStatus(VoucherStatus.DELETED.code());
        entity.setLastAction(LastAction.DELETE.code());
        entity.setVersionNo(nextVersionNo(entity));
        entity.setDeleteReason(request.deleteReason());
        entity.setDeleteOperatorNo(context.operatorNo());
        entity.setDeleteTime(OffsetDateTime.now());
        touch(entity);
        return mapper.toResponse(repository.save(entity));
    }

    public VoucherResponse reviewPass(HeaderContext context, String billId, ReviewPassRequest request) {
        CnapsBillPoc entity = loadLifecycleEntity(context, billId);
        requirePendingReview(entity);
        requireNotSelfReview(context, entity);

        entity.setStatus(VoucherStatus.REVIEW_APPROVED.code());
        entity.setLastAction(LastAction.REVIEW_PASS.code());
        entity.setVersionNo(nextVersionNo(entity));
        entity.setCheckerNo(context.operatorNo());
        entity.setCheckerTime(OffsetDateTime.now());
        entity.setReviewComment(request.reviewComment());
        entity.setRejectReason(null);
        touch(entity);
        return mapper.toResponse(repository.save(entity));
    }

    public VoucherResponse reviewReturn(HeaderContext context, String billId, ReviewReturnRequest request) {
        requireRejectReason(request.rejectReason());
        CnapsBillPoc entity = loadLifecycleEntity(context, billId);
        requirePendingReview(entity);
        requireNotSelfReview(context, entity);

        entity.setStatus(VoucherStatus.REVIEW_REJECTED.code());
        entity.setLastAction(LastAction.REVIEW_RETURN.code());
        entity.setVersionNo(nextVersionNo(entity));
        entity.setCheckerNo(context.operatorNo());
        entity.setCheckerTime(OffsetDateTime.now());
        entity.setRejectReason(request.rejectReason());
        entity.setReviewComment(null);
        touch(entity);
        return mapper.toResponse(repository.save(entity));
    }

    private void validateCreate(VoucherCreateRequest request) {
        if (request.payeeAccountNo() == null || request.payeeAccountNo().isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_EMPTY, "payeeAccountNo is required");
        }
        BigDecimal amount = parseAmount(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "amount must be greater than 0");
        }
        requireDictionaryValue(request.businessType(), SUPPORTED_BUSINESS_TYPES, "businessType");
        requireDictionaryValue(request.priority(), SUPPORTED_PRIORITIES, "priority");
        requireDictionaryValue(request.feeChargeMode(), SUPPORTED_FEE_CHARGE_MODES, "feeChargeMode");
        requireDictionaryValue(request.sendMode(), SUPPORTED_SEND_MODES, "sendMode");
        requireDictionaryValue(request.debitMode(), SUPPORTED_DEBIT_MODES, "debitMode");
        requireDictionaryValue(request.faxFlag(), SUPPORTED_FAX_FLAGS, "faxFlag");
        requireDictionaryValue(request.systemType(), SUPPORTED_SYSTEM_TYPES, "systemType");
    }

    private String nextSerialNo(String maxSerialNo) {
        int next = Integer.parseInt(maxSerialNo) + 1;
        return String.format("%07d", next);
    }

    private String buildBillId(HeaderContext context, String serialNo) {
        return "B" + BILL_ID_DATE_FORMAT.format(context.workDate()) + context.branchNo() + serialNo;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void requireDictionaryValue(String value, List<String> supportedValues, String fieldName) {
        if (value == null || value.isBlank() || !supportedValues.contains(value)) {
            throw new BusinessException(ErrorCode.DICT_VALUE_INVALID, fieldName + " is invalid");
        }
    }

    private CnapsBillPoc loadLifecycleEntity(HeaderContext context, String billId) {
        CnapsBillPoc entity = repository.findByBillId(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "voucher not found"));
        if (!context.branchNo().equals(entity.getBranchNo()) || !context.workDate().equals(entity.getWorkDate())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "voucher not found");
        }
        return entity;
    }

    private void requireEditable(CnapsBillPoc entity) {
        if (!VoucherStatus.PENDING_REVIEW.code().equals(entity.getStatus())
            && !VoucherStatus.REVIEW_REJECTED.code().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_CONFLICT, "voucher status not editable");
        }
    }

    private void requirePendingReview(CnapsBillPoc entity) {
        if (!VoucherStatus.PENDING_REVIEW.code().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_CHANGED, "voucher status changed");
        }
    }

    private void requireNotSelfReview(HeaderContext context, CnapsBillPoc entity) {
        if (context.operatorNo().equals(entity.getOperatorNo())) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_FORBIDDEN, "operator cannot review own voucher");
        }
    }

    private void requireRejectReason(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_EMPTY, "rejectReason is required");
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "amount must be greater than 0");
        }
    }

    private BigDecimal parseOptionalAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "feeAmount must be numeric");
        }
    }

    private Integer nextVersionNo(CnapsBillPoc entity) {
        return entity.getVersionNo() == null ? 1 : entity.getVersionNo() + 1;
    }

    private void touch(CnapsBillPoc entity) {
        OffsetDateTime now = OffsetDateTime.now();
        entity.setUpdatedAt(now);
        entity.setLastActionAt(now);
    }
}
