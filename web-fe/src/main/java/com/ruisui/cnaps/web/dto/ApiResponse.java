package com.ruisui.cnaps.web.dto;

public record ApiResponse<T>(
    String respCode,
    String respMsg,
    T data
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("0000", message, data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
