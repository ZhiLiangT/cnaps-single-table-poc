package com.ruisui.bank.sim.api;

import com.ruisui.bank.sim.domain.BusinessException;
import com.ruisui.bank.sim.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STATUS_CONFLICT, STATUS_CHANGED -> HttpStatus.CONFLICT;
            case SELF_REVIEW_FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
            .body(ApiResponse.fail(requestId(request), ex.getErrorCode().code(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
            .body(ApiResponse.fail(requestId(request), ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader("X-Request-Id");
        }
        return requestId;
    }
}
