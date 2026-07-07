package com.ruisui.bank.sim.service;

import com.ruisui.bank.sim.api.dto.HeaderContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class HeaderContextResolver {

    public HeaderContext resolve(HttpServletRequest request) {
        String requestId = firstNonBlank(request.getHeader("requestId"), request.getHeader("X-Request-Id"));
        String operatorNo = request.getHeader("operatorNo");
        String branchNo = request.getHeader("branchNo");
        String workDateValue = request.getHeader("workDate");
        LocalDate workDate = workDateValue == null || workDateValue.isBlank() ? null : LocalDate.parse(workDateValue);
        return new HeaderContext(requestId, operatorNo, branchNo, workDate);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
