package com.ruisui.cnaps.web.dto;

public record ApiResponse<T>(
    boolean success,
    String respCode,
    String respMsg,
    T data
) {
    public static <T> ApiResponse<T> ok(String requestId, String message, T data) {
        return new ApiResponse<>(true, "0000", message, data);
    }

    public static <T> ApiResponse<T> fail(String requestId, String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
