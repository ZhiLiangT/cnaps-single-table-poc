package com.ruisui.cnaps.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DictServlet extends BaseJsonServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dictType", request.getPathInfo() == null ? "" : request.getPathInfo().replaceFirst("^/", ""));
        callTuxedo(request, response, fields);
    }
}
