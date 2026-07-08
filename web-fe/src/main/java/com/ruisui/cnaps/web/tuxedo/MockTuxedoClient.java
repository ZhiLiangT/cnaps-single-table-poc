package com.ruisui.cnaps.web.tuxedo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class MockTuxedoClient implements TuxedoClient {
    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>(request.fields());
        fields.put("SERVICE_NAME", serviceName);
        fields.put("RESP_CODE", "0000");
        fields.put("RESP_MSG", "success");
        fields.put("SERVER_TIME", OffsetDateTime.now().toString());

        if ("SYSHEALTH".equals(serviceName)) {
            fields.put("STATUS", "UP");
        }
        if ("DICTQRY".equals(serviceName)) {
            fields.put("BUSINESS_TYPE.02102", "普通汇兑");
        }
        if ("BANKQRY".equals(serviceName)) {
            fields.put("BANK.102290000002", "接收行名称");
        }
        if ("CNAPS5701E".equals(serviceName)) {
            fields.put("BILL_ID", "B" + fields.getOrDefault("WORK_DATE", "2026-07-08").toString().replace("-", "") + fields.getOrDefault("BRANCH_NO", "772") + "0002000");
            fields.put("SERIAL_NO", "0002000");
            fields.put("STATUS", "10_PENDING_REVIEW");
        }
        return TuxedoResponse.ok("success", fields);
    }
}
