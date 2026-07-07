package com.ruisui.bank.sim.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "T_CNAPS_BILL_POC")
public class CnapsBillPoc {
    @Id
    @Column(name = "BILL_ID", nullable = false, length = 32)
    private String billId;

    @Column(name = "SERIAL_NO", nullable = false, length = 16)
    private String serialNo;

    @Column(name = "BUSINESS_TYPE", nullable = false, length = 12)
    private String businessType;

    @Column(name = "ACCOUNT_PART1", length = 32)
    private String accountPart1;

    @Column(name = "ACCOUNT_PART2", length = 32)
    private String accountPart2;

    @Column(name = "ACCOUNT_PART3", length = 64)
    private String accountPart3;

    @Column(name = "ACCOUNT_NAME", length = 128)
    private String accountName;

    @Column(name = "PAYER_NAME", length = 128)
    private String payerName;

    @Column(name = "PAYEE_ACCOUNT_NO", nullable = false, length = 64)
    private String payeeAccountNo;

    @Column(name = "PAYEE_NAME", nullable = false, length = 128)
    private String payeeName;

    @Column(name = "PRIORITY", length = 12)
    private String priority;

    @Column(name = "RECEIVE_BANK_NO", length = 32)
    private String receiveBankNo;

    @Column(name = "RECEIVE_BANK_NAME", length = 128)
    private String receiveBankName;

    @Column(name = "SYSTEM_TYPE", length = 16)
    private String systemType;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "DEBIT_MODE", length = 8)
    private String debitMode;

    @Column(name = "FEE_AMOUNT", precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "FEE_CHARGE_MODE", length = 8)
    private String feeChargeMode;

    @Column(name = "SEND_MODE", length = 8)
    private String sendMode;

    @Column(name = "FAX_FLAG", length = 1)
    private String faxFlag;

    @Column(name = "VOUCHER_NO", length = 64)
    private String voucherNo;

    @Column(name = "REMARK", length = 512)
    private String remark;

    @Column(name = "STATUS", nullable = false, length = 32)
    private String status;

    @Column(name = "LAST_ACTION", length = 32)
    private String lastAction;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "OPERATOR_NO", nullable = false, length = 16)
    private String operatorNo;

    @Column(name = "BRANCH_NO", nullable = false, length = 12)
    private String branchNo;

    @Column(name = "WORK_DATE", nullable = false)
    private LocalDate workDate;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "LAST_ACTION_TIME")
    private OffsetDateTime lastActionAt;

    @Column(name = "LAST_OPERATOR_NO", length = 16)
    private String lastOperatorNo;

    @Column(name = "LAST_REQUEST_ID", length = 32)
    private String lastRequestId;

    @Column(name = "REJECT_REASON", length = 200)
    private String rejectReason;

    @Column(name = "DELETE_REASON", length = 200)
    private String deleteReason;

    @Column(name = "DELETE_OPERATOR_NO", length = 16)
    private String deleteOperatorNo;

    @Column(name = "DELETE_TIME")
    private OffsetDateTime deleteTime;

    @Column(name = "CHECKER_NO", length = 16)
    private String checkerNo;

    @Column(name = "CHECKER_TIME")
    private OffsetDateTime checkerTime;

    @Column(name = "REVIEW_COMMENT", length = 512)
    private String reviewComment;

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getAccountPart1() {
        return accountPart1;
    }

    public void setAccountPart1(String accountPart1) {
        this.accountPart1 = accountPart1;
    }

    public String getAccountPart2() {
        return accountPart2;
    }

    public void setAccountPart2(String accountPart2) {
        this.accountPart2 = accountPart2;
    }

    public String getAccountPart3() {
        return accountPart3;
    }

    public void setAccountPart3(String accountPart3) {
        this.accountPart3 = accountPart3;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public String getPayeeAccountNo() {
        return payeeAccountNo;
    }

    public void setPayeeAccountNo(String payeeAccountNo) {
        this.payeeAccountNo = payeeAccountNo;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getReceiveBankNo() {
        return receiveBankNo;
    }

    public void setReceiveBankNo(String receiveBankNo) {
        this.receiveBankNo = receiveBankNo;
    }

    public String getReceiveBankName() {
        return receiveBankName;
    }

    public void setReceiveBankName(String receiveBankName) {
        this.receiveBankName = receiveBankName;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDebitMode() {
        return debitMode;
    }

    public void setDebitMode(String debitMode) {
        this.debitMode = debitMode;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getFeeChargeMode() {
        return feeChargeMode;
    }

    public void setFeeChargeMode(String feeChargeMode) {
        this.feeChargeMode = feeChargeMode;
    }

    public String getSendMode() {
        return sendMode;
    }

    public void setSendMode(String sendMode) {
        this.sendMode = sendMode;
    }

    public String getFaxFlag() {
        return faxFlag;
    }

    public void setFaxFlag(String faxFlag) {
        this.faxFlag = faxFlag;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getOperatorNo() {
        return operatorNo;
    }

    public void setOperatorNo(String operatorNo) {
        this.operatorNo = operatorNo;
    }

    public String getBranchNo() {
        return branchNo;
    }

    public void setBranchNo(String branchNo) {
        this.branchNo = branchNo;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getLastActionAt() {
        return lastActionAt;
    }

    public void setLastActionAt(OffsetDateTime lastActionAt) {
        this.lastActionAt = lastActionAt;
    }

    public String getLastOperatorNo() {
        return lastOperatorNo;
    }

    public void setLastOperatorNo(String lastOperatorNo) {
        this.lastOperatorNo = lastOperatorNo;
    }

    public String getLastRequestId() {
        return lastRequestId;
    }

    public void setLastRequestId(String lastRequestId) {
        this.lastRequestId = lastRequestId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    public String getDeleteOperatorNo() {
        return deleteOperatorNo;
    }

    public void setDeleteOperatorNo(String deleteOperatorNo) {
        this.deleteOperatorNo = deleteOperatorNo;
    }

    public OffsetDateTime getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(OffsetDateTime deleteTime) {
        this.deleteTime = deleteTime;
    }

    public String getCheckerNo() {
        return checkerNo;
    }

    public void setCheckerNo(String checkerNo) {
        this.checkerNo = checkerNo;
    }

    public OffsetDateTime getCheckerTime() {
        return checkerTime;
    }

    public void setCheckerTime(OffsetDateTime checkerTime) {
        this.checkerTime = checkerTime;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }
}
