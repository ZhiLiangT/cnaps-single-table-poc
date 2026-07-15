package com.ruisui.cnaps.web.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonSupport() {
    }

    public static Map<String, Object> readBodyMap(HttpServletRequest request) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength == 0) {
            return new LinkedHashMap<>();
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(request.getInputStream(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            if (contentLength < 0) {
                return new LinkedHashMap<>();
            }
            throw e;
        }
    }

    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), body);
    }
}
