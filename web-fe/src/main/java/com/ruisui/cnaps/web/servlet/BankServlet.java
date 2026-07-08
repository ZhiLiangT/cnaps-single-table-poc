package com.ruisui.cnaps.web.servlet;

import com.ruisui.cnaps.web.support.RequestSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class BankServlet extends BaseJsonServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        callTuxedo(request, response, RequestSupport.queryParams(request));
    }
}
