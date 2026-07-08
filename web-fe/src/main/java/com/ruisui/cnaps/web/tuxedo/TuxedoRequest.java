package com.ruisui.cnaps.web.tuxedo;

import java.util.LinkedHashMap;
import java.util.Map;

public record TuxedoRequest(Map<String, Object> fields) {
    public TuxedoRequest {
        fields = Map.copyOf(new LinkedHashMap<>(fields));
    }
}
