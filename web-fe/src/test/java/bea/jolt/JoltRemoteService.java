package bea.jolt;

import java.util.HashMap;
import java.util.Map;

public class JoltRemoteService {
    private final String serviceName;
    private final Map<String, String> strings = new HashMap<>();

    public JoltRemoteService(String serviceName, JoltSession session) {
        this.serviceName = serviceName;
    }

    public void setString(String name, String value) {
        strings.put(name, value);
    }

    public void setLong(String name, long value) {
    }

    public void call(Object transaction) throws ApplicationException {
        if ("RUNTIME_FAILURE".equals(serviceName)) {
            throw new IllegalStateException("simulated transport failure");
        }
        if ("DICTQRY_APPLICATION_ERROR".equals(serviceName)) {
            JoltRemoteService error = new JoltRemoteService(serviceName, null);
            error.strings.put("RESP_CODE", "2003");
            error.strings.put("RESP_MSG", "unknown dictionary type");
            throw new ApplicationException(error);
        }
    }

    public String getStringDef(String name, String defaultValue) {
        return strings.getOrDefault(name, defaultValue);
    }
}
