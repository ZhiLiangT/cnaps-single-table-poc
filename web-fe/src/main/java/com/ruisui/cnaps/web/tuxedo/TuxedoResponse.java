package com.ruisui.cnaps.web.tuxedo;

import java.util.LinkedHashMap;
import java.util.Map;

public record TuxedoResponse(boolean success, String respCode, String respMsg, Map<String, Object> fields) {
    public TuxedoResponse {
        fields = Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static TuxedoResponse ok(String message, Map<String, Object> fields) {
        return new TuxedoResponse(true, "0000", message, fields);
    }

    public static TuxedoResponse fail(String code, String message) {
        return new TuxedoResponse(false, code, message, Map.of());
    }
}
