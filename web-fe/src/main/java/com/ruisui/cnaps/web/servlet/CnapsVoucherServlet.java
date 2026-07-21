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
        if (isRejectedGetPath(apiPath) || isReviewPath(apiPath)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>(RequestSupport.queryParams(request));
        RequestSupport.includeBillPath(fields, request.getPathInfo());
        callTuxedo(request, response, fields);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String apiPath = RequestSupport.apiPath(request);
        if (isReviewActionPath(apiPath)) {
            handleReviewActionPost(request, response);
            return;
        }
        Map<String, Object> fields = JsonSupport.readBodyMap(request);
        boolean listPost = isListPostPath(apiPath);
        boolean reviewListPost = isReviewListPostPath(apiPath);
        if (listPost && !validateListFilters(fields, reviewListPost, response)) {
            return;
        }
        if (reviewListPost) {
            fields = reviewListFields(fields);
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
        return "/api/cnaps/vouchers/query".equals(apiPath)
            || isReviewListPostPath(apiPath);
    }

    private static boolean isReviewListPostPath(String apiPath) {
        return "/api/cnaps/vouchers/review-list".equals(apiPath);
    }

    private static boolean isRejectedGetPath(String apiPath) {
        return "/api/cnaps/vouchers".equals(apiPath)
            || "/api/cnaps/vouchers/query".equals(apiPath);
    }

    private static boolean isReviewPath(String apiPath) {
        return isReviewListPostPath(apiPath) || isReviewActionPath(apiPath);
    }

    private static boolean isReviewActionPath(String apiPath) {
        return apiPath.endsWith("/review-pass") || apiPath.endsWith("/review-return");
    }

    private void handleReviewActionPost(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        String billId = RequestSupport.reviewActionBillId(request.getPathInfo());
        if (billId == null || billId.isBlank()) {
            JsonSupport.write(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                ApiResponse.fail("2001", "billId is required")
            );
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("billId", billId);
        callTuxedo(request, response, fields);
    }

    private static Map<String, Object> reviewListFields(Map<String, Object> fields) {
        Map<String, Object> allowed = new LinkedHashMap<>();
        copyIfPresent(fields, allowed, "startWorkDate");
        copyIfPresent(fields, allowed, "endWorkDate");
        copyIfPresent(fields, allowed, "serialNo");
        copyIfPresent(fields, allowed, "pageNo");
        copyIfPresent(fields, allowed, "pageSize");
        allowed.put("status", "10_PENDING_REVIEW");
        return allowed;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private boolean validateListFilters(
        Map<String, Object> fields,
        boolean reviewListPost,
        HttpServletResponse response
    ) throws IOException {
        String validationError = RequestSupport.validateWorkDateFilter(fields);
        if (validationError == null && reviewListPost) {
            validationError = RequestSupport.validateReviewPageFilter(fields);
        }
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
