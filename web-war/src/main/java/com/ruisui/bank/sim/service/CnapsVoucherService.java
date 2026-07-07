package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import com.ruisui.bank.sim.api.dto.VoucherCreateRequest;
import com.ruisui.bank.sim.api.dto.VoucherResponse;
import com.ruisui.bank.sim.domain.BusinessException;
import com.ruisui.bank.sim.domain.ErrorCode;
import com.ruisui.bank.sim.persistence.CnapsBillPoc;
import com.ruisui.bank.sim.persistence.CnapsBillPocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class CnapsVoucherService {
    private static final DateTimeFormatter BILL_ID_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<String> SUPPORTED_BUSINESS_TYPES = List.of("02102");

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
        BigDecimal amount = new BigDecimal(request.amount());
        BigDecimal feeAmount = request.feeAmount() == null || request.feeAmount().isBlank()
            ? BigDecimal.ZERO
            : new BigDecimal(request.feeAmount());

        CnapsBillPoc entity = mapper.toEntity(context, request, billId, serialNo, amount, feeAmount);
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> query(HeaderContext context, String status, String operatorNo, String serialNo, int page, int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.ASC, "serialNo"));
        Page<CnapsBillPoc> result = status == null || status.isBlank()
            ? repository.findByWorkDateAndBranchNo(context.workDate(), context.branchNo(), pageable)
            : repository.findByWorkDateAndBranchNoAndStatus(context.workDate(), context.branchNo(), status, pageable);

        if ((operatorNo == null || operatorNo.isBlank()) && (serialNo == null || serialNo.isBlank())) {
            return mapper.toResponsePage(result);
        }

        List<VoucherResponse> filtered = result.getContent().stream()
            .filter(item -> operatorNo == null || operatorNo.isBlank() || operatorNo.equals(item.getOperatorNo()))
            .filter(item -> serialNo == null || serialNo.isBlank() || serialNo.equals(item.getSerialNo()))
            .map(mapper::toResponse)
            .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
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

    private void validateCreate(VoucherCreateRequest request) {
        if (request.payeeAccountNo() == null || request.payeeAccountNo().isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_EMPTY, "payeeAccountNo is required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(request.amount());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "amount must be greater than 0");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "amount must be greater than 0");
        }
        if (!SUPPORTED_BUSINESS_TYPES.contains(request.businessType())) {
            throw new BusinessException(ErrorCode.DICT_VALUE_INVALID, "businessType is invalid");
        }
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
}
