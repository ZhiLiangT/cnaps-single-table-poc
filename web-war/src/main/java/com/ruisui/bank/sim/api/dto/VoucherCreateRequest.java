package com.ruisui.bank.sim.api.dto;

public record VoucherCreateRequest(
    String businessType,
    String accountPart1,
    String accountPart2,
    String accountPart3,
    String accountName,
    String payerName,
    String payeeAccountNo,
    String payeeName,
    String priority,
    String receiveBankNo,
    String receiveBankName,
    String systemType,
    String amount,
    String debitMode,
    String feeAmount,
    String feeChargeMode,
    String sendMode,
    String faxFlag,
    String voucherNo,
    String remark
) {
}
