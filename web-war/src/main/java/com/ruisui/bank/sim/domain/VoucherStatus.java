package com.ruisui.bank.sim.domain;

public enum VoucherStatus {
    PENDING_REVIEW("10_PENDING_REVIEW"),
    REVIEW_APPROVED("20_REVIEW_APPROVED"),
    REVIEW_REJECTED("30_REVIEW_REJECTED"),
    DELETED("40_DELETED");

    private final String code;

    VoucherStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
