package com.ruisui.cnaps.web.servlet;

import com.ruisui.cnaps.web.dto.ApiResponse;
import com.ruisui.cnaps.web.support.JsonSupport;
import com.ruisui.cnaps.web.support.RequestSupport;
import com.ruisui.cnaps.web.tuxedo.TuxedoClient;
import com.ruisui.cnaps.web.tuxedo.TuxedoClientProvider;
import com.ruisui.cnaps.web.tuxedo.TuxedoRequest;
import com.ruisui.cnaps.web.tuxedo.TuxedoRequestMapper;
import com.ruisui.cnaps.web.tuxedo.TuxedoResponse;
import com.ruisui.cnaps.web.tuxedo.TuxedoResponseMapper;
import com.ruisui.cnaps.web.tuxedo.TuxedoRuntimeConfig;

import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

abstract class BaseJsonServlet extends HttpServlet {
    protected TuxedoClient tuxedoClient;
    protected TuxedoRequestMapper requestMapper;
    private TuxedoResponseMapper responseMapper;
    private String operatorNo;
    private String branchNo;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        TuxedoRuntimeConfig config = TuxedoClientProvider.config(context);
        this.tuxedoClient = TuxedoClientProvider.get(context);
        this.requestMapper = new TuxedoRequestMapper();
        this.responseMapper = new TuxedoResponseMapper();
        this.operatorNo = config.pocOperatorNo();
        this.branchNo = config.pocBranchNo();
    }

    protected void callTuxedo(HttpServletRequest request, HttpServletResponse response, Map<String, Object> fields)
        throws IOException {
        String requestId = RequestSupport.newRequestId();
        TuxedoRequest tuxedoRequest = requestMapper.from(
            requestId,
            operatorNo,
            branchNo,
            fields
        );
        TuxedoResponse tuxedoResponse = tuxedoClient.call(
            requestMapper.serviceName(request.getMethod(), RequestSupport.apiPath(request)),
            tuxedoRequest
        );
        ApiResponse<Object> apiResponse = responseMapper.toApiResponse(requestId, tuxedoResponse);
        JsonSupport.write(response, httpStatus(tuxedoResponse.respCode()), apiResponse);
    }

    private static int httpStatus(String respCode) {
        return switch (respCode) {
            case "0000" -> HttpServletResponse.SC_OK;
            case "2001", "2002", "2003" -> HttpServletResponse.SC_BAD_REQUEST;
            case "3001" -> HttpServletResponse.SC_NOT_FOUND;
            case "3002", "3003", "3004" -> HttpServletResponse.SC_CONFLICT;
            case "3005" -> HttpServletResponse.SC_FORBIDDEN;
            case "4002" -> HttpServletResponse.SC_GATEWAY_TIMEOUT;
            case "4003" -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            default -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
    }
}
