package com.ruisui.cnaps.web.servlet;

import com.ruisui.cnaps.web.dto.ApiResponse;
import com.ruisui.cnaps.web.support.JsonSupport;
import com.ruisui.cnaps.web.support.RequestSupport;
import com.ruisui.cnaps.web.tuxedo.TuxedoClient;
import com.ruisui.cnaps.web.tuxedo.TuxedoRequestMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CnapsVoucherServlet extends BaseJsonServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>(RequestSupport.queryParams(request));
        String apiPath = RequestSupport.apiPath(request);
        if ("/api/cnaps/vouchers".equals(apiPath) || "/api/cnaps/vouchers/review-list".equals(apiPath)) {
            String validationError = RequestSupport.validateWorkDateFilter(fields);
            if (validationError != null) {
                JsonSupport.write(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    ApiResponse.fail("2002", validationError)
                );
                return;
            }
        }
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = JsonSupport.readBodyMap(request);
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = JsonSupport.readBodyMap(request);
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }
}
