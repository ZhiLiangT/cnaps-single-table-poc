package com.ruisui.bank.sim.domain;

public enum VoucherStatus {
    PENDING_REVIEW("10_PENDING_REVIEW");

    private final String code;

    VoucherStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
