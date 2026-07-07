package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import com.ruisui.bank.sim.api.dto.VoucherCreateRequest;
import com.ruisui.bank.sim.api.dto.VoucherResponse;
import com.ruisui.bank.sim.domain.LastAction;
import com.ruisui.bank.sim.domain.VoucherStatus;
import com.ruisui.bank.sim.persistence.CnapsBillPoc;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class VoucherMapper {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public CnapsBillPoc toEntity(
        HeaderContext context,
        VoucherCreateRequest request,
        String billId,
        String serialNo,
        BigDecimal amount,
        BigDecimal feeAmount
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        CnapsBillPoc entity = new CnapsBillPoc();
        entity.setBillId(billId);
        entity.setSerialNo(serialNo);
        applySubmissionFields(entity, request, amount, feeAmount);
        entity.setStatus(VoucherStatus.PENDING_REVIEW.code());
        entity.setLastAction(LastAction.CREATE.code());
        entity.setVersionNo(1);
        entity.setOperatorNo(context.operatorNo());
        entity.setBranchNo(context.branchNo());
        entity.setWorkDate(context.workDate());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastActionAt(now);
        entity.setLastOperatorNo(context.operatorNo());
        entity.setLastRequestId(context.requestId());
        return entity;
    }

    public void applySubmissionFields(CnapsBillPoc entity, VoucherCreateRequest request, BigDecimal amount, BigDecimal feeAmount) {
        entity.setBusinessType(request.businessType());
        entity.setAccountPart1(request.accountPart1());
        entity.setAccountPart2(request.accountPart2());
        entity.setAccountPart3(request.accountPart3());
        entity.setAccountName(request.accountName());
        entity.setPayerName(request.payerName());
        entity.setPayeeAccountNo(request.payeeAccountNo());
        entity.setPayeeName(request.payeeName());
        entity.setPriority(request.priority());
        entity.setReceiveBankNo(request.receiveBankNo());
        entity.setReceiveBankName(request.receiveBankName());
        entity.setSystemType(request.systemType());
        entity.setAmount(amount);
        entity.setDebitMode(request.debitMode());
        entity.setFeeAmount(feeAmount);
        entity.setFeeChargeMode(request.feeChargeMode());
        entity.setSendMode(request.sendMode());
        entity.setFaxFlag(request.faxFlag());
        entity.setVoucherNo(request.voucherNo());
        entity.setRemark(request.remark());
        entity.setAmount(amount);
        entity.setFeeAmount(feeAmount);
    }

    public VoucherResponse toResponse(CnapsBillPoc entity) {
        return new VoucherResponse(
            entity.getBillId(),
            entity.getSerialNo(),
            entity.getStatus(),
            entity.getLastAction(),
            entity.getBusinessType(),
            entity.getAccountPart1(),
            entity.getAccountPart2(),
            entity.getAccountPart3(),
            entity.getAccountName(),
            entity.getPayerName(),
            entity.getPayeeAccountNo(),
            entity.getPayeeName(),
            entity.getPriority(),
            entity.getReceiveBankNo(),
            entity.getReceiveBankName(),
            entity.getSystemType(),
            toText(entity.getAmount()),
            entity.getDebitMode(),
            toText(entity.getFeeAmount()),
            entity.getFeeChargeMode(),
            entity.getSendMode(),
            entity.getFaxFlag(),
            entity.getVoucherNo(),
            entity.getRemark(),
            entity.getOperatorNo(),
            entity.getBranchNo(),
            entity.getLastOperatorNo(),
            entity.getLastRequestId(),
            entity.getWorkDate() == null ? null : DATE_FORMAT.format(entity.getWorkDate()),
            entity.getVersionNo(),
            entity.getRejectReason(),
            entity.getDeleteOperatorNo(),
            toText(entity.getDeleteTime()),
            entity.getCheckerNo(),
            toText(entity.getCheckerTime()),
            entity.getReviewComment()
        );
    }

    public Page<VoucherResponse> toResponsePage(Page<CnapsBillPoc> page) {
        return page.map(this::toResponse);
    }

    public List<VoucherResponse> toResponseList(List<CnapsBillPoc> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private String toText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String toText(OffsetDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
