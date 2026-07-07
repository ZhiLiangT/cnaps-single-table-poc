package com.ruisui.bank.sim.domain;

public enum ErrorCode {
    SUCCESS("0000", "success"),
    REQUIRED_FIELD_EMPTY("2001", "required field empty"),
    FIELD_FORMAT_ERROR("2002", "field format error"),
    DICT_VALUE_INVALID("2003", "dictionary value invalid"),
    NOT_FOUND("3001", "not found"),
    STATUS_CONFLICT("3003", "status conflict"),
    STATUS_CHANGED("3004", "status changed"),
    SELF_REVIEW_FORBIDDEN("3005", "self review forbidden"),
    PERSISTENCE_ERROR("4001", "persistence error"),
    TUXEDO_TIMEOUT("4002", "tuxedo timeout"),
    TUXEDO_UNAVAILABLE("4003", "tuxedo unavailable"),
    UNKNOWN_ERROR("9999", "unknown error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
