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
        String apiPath = RequestSupport.apiPath(request);
        if (isRejectedGetPath(apiPath) || isRemovedReviewPath(apiPath)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>(RequestSupport.queryParams(request));
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = JsonSupport.readBodyMap(request);
        String apiPath = RequestSupport.apiPath(request);
        if (isRemovedReviewPath(apiPath)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        boolean listPost = isListPostPath(apiPath);
        if (listPost && !validateListFilters(fields, response)) {
            return;
        }
        if (!listPost) {
            RequestSupport.includeBillPath(fields, request.getPathInfo());
        }
        callTuxedo(request, response, fields);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = JsonSupport.readBodyMap(request);
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }

    private static boolean isListPostPath(String apiPath) {
        return "/api/cnaps/vouchers/query".equals(apiPath);
    }

    private static boolean isRejectedGetPath(String apiPath) {
        return "/api/cnaps/vouchers".equals(apiPath)
            || "/api/cnaps/vouchers/query".equals(apiPath);
    }

    private static boolean isRemovedReviewPath(String apiPath) {
        return "/api/cnaps/vouchers/review-list".equals(apiPath)
            || apiPath.endsWith("/review-pass")
            || apiPath.endsWith("/review-return");
    }

    private boolean validateListFilters(Map<String, Object> fields, HttpServletResponse response) throws IOException {
        String validationError = RequestSupport.validateWorkDateFilter(fields);
        if (validationError == null) {
            return true;
        }
        JsonSupport.write(
            response,
            HttpServletResponse.SC_BAD_REQUEST,
            ApiResponse.fail("2002", validationError)
        );
        return false;
    }
}
