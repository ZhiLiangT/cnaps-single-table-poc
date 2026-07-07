package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import com.ruisui.bank.sim.domain.BusinessException;
import com.ruisui.bank.sim.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class HeaderContextResolver {

    public HeaderContext resolve(HttpServletRequest request) {
        String requestId = required(firstNonBlank(request.getHeader("requestId"), request.getHeader("X-Request-Id")), "requestId");
        String operatorNo = required(request.getHeader("operatorNo"), "operatorNo");
        String branchNo = required(request.getHeader("branchNo"), "branchNo");
        String workDateValue = required(request.getHeader("workDate"), "workDate");
        LocalDate workDate;
        try {
            workDate = LocalDate.parse(workDateValue);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "workDate must be yyyy-MM-dd");
        }
        String channel = firstNonBlank(request.getHeader("channel"), "WEBFE");
        return new HeaderContext(requestId, operatorNo, branchNo, workDate, channel);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_EMPTY, fieldName + " is required");
        }
        return value;
    }
}
