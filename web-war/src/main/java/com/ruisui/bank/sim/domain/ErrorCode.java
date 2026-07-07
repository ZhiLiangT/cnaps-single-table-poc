package com.ruisui.bank.sim.domain;

public enum ErrorCode {
    SUCCESS("0000", "success"),
    BAD_REQUEST("4000", "bad request"),
    REQUIRED_FIELD_EMPTY("2001", "required field empty"),
    FIELD_FORMAT_ERROR("2002", "field format error"),
    DICT_VALUE_INVALID("2003", "dictionary value invalid"),
    NOT_FOUND("4040", "not found"),
    INTERNAL_ERROR("5000", "internal error");

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
