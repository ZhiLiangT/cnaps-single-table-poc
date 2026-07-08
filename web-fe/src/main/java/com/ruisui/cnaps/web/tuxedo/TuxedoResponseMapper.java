package com.ruisui.cnaps.web.tuxedo;

import com.ruisui.cnaps.web.dto.ApiResponse;

import java.util.Map;

public class TuxedoResponseMapper {
    public ApiResponse<Map<String, Object>> toApiResponse(String requestId, TuxedoResponse response) {
        if (response.success()) {
            return ApiResponse.ok(requestId, response.respMsg(), response.fields());
        }
        return ApiResponse.fail(requestId, response.respCode(), response.respMsg());
    }
}
