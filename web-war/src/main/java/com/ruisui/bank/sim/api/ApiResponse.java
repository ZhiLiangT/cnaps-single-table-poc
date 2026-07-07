package com.ruisui.bank.sim.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
    boolean success,
    String respCode,
    String respMsg,
    String requestId,
    OffsetDateTime serverTime,
    T data
) {
    public static <T> ApiResponse<T> ok(String requestId, String message, T data) {
        return new ApiResponse<>(true, "0000", message, requestId, OffsetDateTime.now(), data);
    }

    public static <T> ApiResponse<T> fail(String requestId, String code, String message) {
        return new ApiResponse<>(false, code, message, requestId, OffsetDateTime.now(), null);
    }
}
