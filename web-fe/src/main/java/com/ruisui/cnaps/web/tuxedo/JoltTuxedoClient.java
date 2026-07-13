package com.ruisui.cnaps.web.tuxedo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JoltTuxedoClient implements TuxedoClient {
    private static final int MAX_RESPONSE_OCCURRENCES = 1_000;
    private static final List<String> ENVELOPE_FIELDS = List.of("RESP_CODE", "RESP_MSG");
    private static final List<String> PAGE_SERVICES = List.of("BANKQRY", "CNAPS4609Q", "CNAPS5702Q");
    private static final List<String> OPERATOR_NO_OUTPUT_ONLY_SERVICES = List.of("CNAPS4609Q", "CNAPS5702Q");
    private static final List<String> DICTIONARY_FIELDS = List.of(
        "DICT_TYPE", "DICT_CODE", "DICT_NAME", "SORT_NO"
    );
    private static final List<String> BANK_FIELDS = List.of(
        "BANK_NO", "BANK_NAME", "CITY", "SYSTEM_TYPE"
    );
    private static final List<String> VOUCHER_FIELDS = List.of(
        "BILL_ID", "WORK_DATE", "BRANCH_NO", "OPERATOR_NO", "SERIAL_NO", "BUSINESS_TYPE",
        "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3", "ACCOUNT_NAME", "PAYER_NAME",
        "PAYEE_ACCT", "PAYEE_NAME", "PRIORITY", "RECEIVE_BANK_NO", "RECEIVE_BANK_NAME",
        "SYSTEM_TYPE", "AMOUNT", "DEBIT_MODE", "FEE_AMOUNT", "FEE_CHARGE_MODE", "SEND_MODE",
        "FAX_FLAG", "VOUCHER_NO", "REMARK", "STATUS", "CHECKER_NO", "CHECKER_TIME",
        "REJECT_REASON", "REVIEW_COMMENT", "DELETE_REASON", "DELETE_OPERATOR_NO", "DELETE_TIME",
        "LAST_ACTION", "LAST_OPERATOR_NO", "LAST_REQUEST_ID", "LAST_ACTION_TIME", "CREATED_AT",
        "UPDATED_AT", "VERSION_NO"
    );
    private static final List<String> RESPONSE_FIELDS = List.of(
        "RESP_CODE", "RESP_MSG", "WEBFE", "TUXEDO", "ORACLE", "SERVICE", "CHECK_TIME",
        "DICT_TYPE", "DICT_CODE", "DICT_NAME", "SORT_NO", "BANK_NO", "BANK_NAME", "CITY",
        "BILL_ID", "SERIAL_NO", "WORK_DATE", "BRANCH_NO", "OPERATOR_NO", "BUSINESS_TYPE", "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3",
        "ACCOUNT_NAME", "PAYER_NAME", "PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME",
        "PAYEE_ACCT", "PAYEE_NAME", "PRIORITY", "RECEIVE_BANK_NO",
        "RECEIVE_BANK_NAME", "SYSTEM_TYPE", "AMOUNT", "DEBIT_MODE", "FEE_AMOUNT", "FEE_CHARGE_MODE",
        "SEND_MODE", "FAX_FLAG", "VOUCHER_NO", "REMARK", "STATUS", "REJECT_REASON", "REVIEW_COMMENT",
        "DELETE_REASON", "DELETE_OPERATOR_NO", "DELETE_TIME", "CHECKER_NO", "CHECKER_TIME", "LAST_ACTION", "INCLUDE_DELETED",
        "LAST_OPERATOR_NO", "LAST_REQUEST_ID", "LAST_ACTION_TIME", "CREATED_AT", "UPDATED_AT", "VERSION_NO",
        "PAGE_NO", "PAGE_SIZE", "TOTAL_ELEMENTS", "TOTAL_PAGES"
    );
    private static final List<String> NUMERIC_FIELDS = List.of(
        "PAGE_NO", "PAGE_SIZE", "TOTAL_ELEMENTS", "TOTAL_PAGES", "SORT_NO", "VERSION_NO"
    );

    private final TuxedoRuntimeConfig config;
    private final ClassLoader classLoader;

    public JoltTuxedoClient() {
        this(TuxedoRuntimeConfig.defaults("jolt"));
    }

    public JoltTuxedoClient(TuxedoRuntimeConfig config) {
        this(config, contextClassLoader());
    }

    JoltTuxedoClient(TuxedoRuntimeConfig config, ClassLoader classLoader) {
        this.config = config;
        this.classLoader = classLoader == null ? JoltTuxedoClient.class.getClassLoader() : classLoader;
    }

    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        Object session = null;
        try {
            Class<?> attributesClass = Class.forName("bea.jolt.JoltSessionAttributes", true, classLoader);
            Class<?> sessionClass = Class.forName("bea.jolt.JoltSession", true, classLoader);
            Class<?> remoteServiceClass = Class.forName("bea.jolt.JoltRemoteService", true, classLoader);

            Object attributes = attributesClass.getConstructor().newInstance();
            setJoltAttribute(attributesClass, attributes, "APPADDRESS", config.joltListen());
            int receiveTimeoutSeconds = (int)Math.max(1L, (config.joltTimeoutMillis() + 999L) / 1_000L);
            setJoltAttribute(attributesClass, attributes, "RECVTIMEOUT", String.valueOf(receiveTimeoutSeconds));

            session = sessionClass
                .getConstructor(attributesClass, String.class, String.class, String.class, String.class)
                .newInstance(
                    attributes,
                    config.joltUserName(),
                    config.joltUserRole(),
                    config.joltUserPassword(),
                    config.joltAppPassword()
                );

            Object remoteService = remoteServiceConstructor(remoteServiceClass, sessionClass)
                .newInstance(serviceName, session);
            for (Map.Entry<String, Object> field : request.fields().entrySet()) {
                if (shouldWriteRequestField(serviceName, field.getKey())) {
                    putField(remoteServiceClass, remoteService, field.getKey(), field.getValue());
                }
            }
            try {
                callRemoteService(remoteServiceClass, remoteService);
            } catch (InvocationTargetException ex) {
                Object applicationErrorService = applicationErrorService(ex.getTargetException());
                if (applicationErrorService == null) {
                    throw ex;
                }
                remoteService = applicationErrorService;
                remoteServiceClass = remoteService.getClass();
            }

            Map<String, Object> fields = readResponseFields(serviceName, remoteServiceClass, remoteService);
            String respCode = stringValue(fields.getOrDefault("RESP_CODE", "0000"));
            String respMsg = stringValue(fields.getOrDefault("RESP_MSG", "success"));
            if ("0000".equals(respCode)) {
                return TuxedoResponse.ok(respMsg, fields);
            }
            return TuxedoResponse.fail(respCode, respMsg);
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return TuxedoResponse.fail("4003", "Jolt runtime classes not found: " + ex.getMessage());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable root = ex instanceof InvocationTargetException invocation && invocation.getTargetException() != null
                ? invocation.getTargetException()
                : ex;
            return TuxedoResponse.fail("4002", "Tuxedo service call failed: " + root.getMessage());
        } finally {
            endSession(session);
        }
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? JoltTuxedoClient.class.getClassLoader() : loader;
    }

    private void setJoltAttribute(Class<?> attributesClass, Object attributes, String fieldName, String value)
        throws ReflectiveOperationException {
        if (value == null || value.isBlank()) {
            return;
        }
        Object key = attributesClass.getField(fieldName).get(attributes);
        if (fieldName.endsWith("TIMEOUT") && isInteger(value)) {
            Method setInt = method(attributesClass, "setInt", key.getClass(), int.class);
            if (setInt != null) {
                setInt.invoke(attributes, key, Integer.parseInt(value));
                return;
            }
        }
        Method method = method(attributesClass, "setString", key.getClass(), String.class);
        if (method == null && key instanceof Integer) {
            method = method(attributesClass, "setString", int.class, String.class);
        }
        if (method != null) {
            method.invoke(attributes, key, value);
        }
    }

    private Constructor<?> remoteServiceConstructor(Class<?> remoteServiceClass, Class<?> sessionClass)
        throws NoSuchMethodException {
        for (Constructor<?> constructor : remoteServiceClass.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 2
                && parameterTypes[0].equals(String.class)
                && parameterTypes[1].isAssignableFrom(sessionClass)) {
                return constructor;
            }
        }
        return remoteServiceClass.getConstructor(String.class, sessionClass);
    }

    private void putField(Class<?> remoteServiceClass, Object remoteService, String name, Object value)
        throws ReflectiveOperationException {
        if (value == null) {
            return;
        }
        String text = stringValue(value);
        if (NUMERIC_FIELDS.contains(name) && isLong(text)) {
            long numericValue = Long.parseLong(text);
            Method setInt = method(remoteServiceClass, "setInt", String.class, int.class);
            if (setInt != null && numericValue >= Integer.MIN_VALUE && numericValue <= Integer.MAX_VALUE) {
                setInt.invoke(remoteService, name, (int)numericValue);
                return;
            }
            Method setLong = method(remoteServiceClass, "setLong", String.class, long.class);
            if (setLong != null) {
                setLong.invoke(remoteService, name, numericValue);
                return;
            }
        }
        Method setString = method(remoteServiceClass, "setString", String.class, String.class);
        if (setString != null) {
            setString.invoke(remoteService, name, text);
            return;
        }
        Method addString = method(remoteServiceClass, "addString", String.class, String.class);
        if (addString != null) {
            addString.invoke(remoteService, name, text);
        }
    }

    private boolean shouldWriteRequestField(String serviceName, String fieldName) {
        return !("OPERATOR_NO".equals(fieldName) && OPERATOR_NO_OUTPUT_ONLY_SERVICES.contains(serviceName));
    }

    private Map<String, Object> readResponseFields(
        String serviceName,
        Class<?> remoteServiceClass,
        Object remoteService
    )
        throws ReflectiveOperationException {
        if ("DICTQRY".equals(serviceName)) {
            Map<String, Object> fields = readFields(remoteServiceClass, remoteService, ENVELOPE_FIELDS);
            fields.put("_DATA", readOccurrences(
                remoteServiceClass,
                remoteService,
                "DICT_CODE",
                DICTIONARY_FIELDS,
                MAX_RESPONSE_OCCURRENCES
            ));
            return fields;
        }
        if (PAGE_SERVICES.contains(serviceName)) {
            Map<String, Object> fields = readFields(remoteServiceClass, remoteService, ENVELOPE_FIELDS);
            long pageNo = positiveLong(getLong(remoteServiceClass, remoteService, "PAGE_NO"), 1L);
            long pageSize = positiveLong(getLong(remoteServiceClass, remoteService, "PAGE_SIZE"), 10L);
            long total = nonNegativeLong(getLong(remoteServiceClass, remoteService, "TOTAL_ELEMENTS"), 0L);
            List<String> recordFields = "BANKQRY".equals(serviceName) ? BANK_FIELDS : VOUCHER_FIELDS;
            String primaryField = "BANKQRY".equals(serviceName) ? "BANK_NO" : "BILL_ID";
            int limit = (int)Math.min(Math.min(pageSize, total), MAX_RESPONSE_OCCURRENCES);

            Map<String, Object> page = new LinkedHashMap<>();
            page.put("PAGE_NO", pageNo);
            page.put("PAGE_SIZE", pageSize);
            page.put("TOTAL", total);
            page.put("RECORDS", readOccurrences(
                remoteServiceClass,
                remoteService,
                primaryField,
                recordFields,
                limit
            ));
            fields.put("_DATA", page);
            return fields;
        }
        return readFields(remoteServiceClass, remoteService, RESPONSE_FIELDS);
    }

    private Map<String, Object> readFields(
        Class<?> remoteServiceClass,
        Object remoteService,
        List<String> fieldNames
    ) throws ReflectiveOperationException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            Object value = NUMERIC_FIELDS.contains(fieldName)
                ? getLong(remoteServiceClass, remoteService, fieldName)
                : getString(remoteServiceClass, remoteService, fieldName);
            if (value != null && !"".equals(value)) {
                fields.put(fieldName, value);
            }
        }
        return fields;
    }

    private List<Map<String, Object>> readOccurrences(
        Class<?> remoteServiceClass,
        Object remoteService,
        String primaryField,
        List<String> fieldNames,
        int limit
    ) throws ReflectiveOperationException {
        java.util.ArrayList<Map<String, Object>> records = new java.util.ArrayList<>();
        for (int occurrence = 0; occurrence < limit; occurrence++) {
            Object primaryValue = getItem(remoteServiceClass, remoteService, primaryField, occurrence);
            if (primaryValue == null || "".equals(primaryValue)) {
                break;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put(primaryField, primaryValue);
            for (String fieldName : fieldNames) {
                if (primaryField.equals(fieldName)) {
                    continue;
                }
                Object value = getItem(remoteServiceClass, remoteService, fieldName, occurrence);
                if (value != null && !"".equals(value)) {
                    record.put(fieldName, value);
                }
            }
            records.add(record);
        }
        return List.copyOf(records);
    }

    private Object getItem(Class<?> remoteServiceClass, Object remoteService, String fieldName, int occurrence)
        throws ReflectiveOperationException {
        Method stringMethod = method(
            remoteServiceClass,
            "getStringItemDef",
            String.class,
            int.class,
            String.class
        );
        Object value = stringMethod == null
            ? null
            : invokeGetterOrNull(stringMethod, remoteService, fieldName, occurrence, null);
        if (value == null && NUMERIC_FIELDS.contains(fieldName)) {
            Method intMethod = method(
                remoteServiceClass,
                "getIntItemDef",
                String.class,
                int.class,
                int.class
            );
            value = intMethod == null
                ? null
                : invokeGetterOrNull(intMethod, remoteService, fieldName, occurrence, Integer.MIN_VALUE);
            if (Integer.valueOf(Integer.MIN_VALUE).equals(value)) {
                value = null;
            }
        }
        if (value == null && NUMERIC_FIELDS.contains(fieldName)) {
            Method longMethod = method(
                remoteServiceClass,
                "getLongItemDef",
                String.class,
                int.class,
                long.class
            );
            value = longMethod == null
                ? null
                : invokeGetterOrNull(longMethod, remoteService, fieldName, occurrence, Long.MIN_VALUE);
            if (Long.valueOf(Long.MIN_VALUE).equals(value)) {
                value = null;
            }
        }
        if (value instanceof String text && NUMERIC_FIELDS.contains(fieldName) && isLong(text)) {
            return Long.parseLong(text);
        }
        if (value instanceof Number number && NUMERIC_FIELDS.contains(fieldName)) {
            return number.longValue();
        }
        return value;
    }

    private long positiveLong(Object value, long defaultValue) {
        long parsed = longValue(value, defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }

    private long nonNegativeLong(Object value, long defaultValue) {
        long parsed = longValue(value, defaultValue);
        return parsed >= 0 ? parsed : defaultValue;
    }

    private long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = stringValue(value);
        return text != null && isLong(text) ? Long.parseLong(text) : defaultValue;
    }

    private Object getString(Class<?> remoteServiceClass, Object remoteService, String fieldName)
        throws ReflectiveOperationException {
        Method method = method(remoteServiceClass, "getStringDef", String.class, String.class);
        if (method != null) {
            return invokeGetterOrNull(method, remoteService, fieldName, null);
        }
        Method itemMethod = method(remoteServiceClass, "getStringItemDef", String.class, int.class, String.class);
        if (itemMethod != null) {
            return invokeGetterOrNull(itemMethod, remoteService, fieldName, 0, null);
        }
        return null;
    }

    private Object getLong(Class<?> remoteServiceClass, Object remoteService, String fieldName)
        throws ReflectiveOperationException {
        Method intMethod = method(remoteServiceClass, "getIntDef", String.class, int.class);
        if (intMethod != null) {
            Object result = invokeGetterOrNull(intMethod, remoteService, fieldName, Integer.MIN_VALUE);
            if (result instanceof Number value && value.intValue() != Integer.MIN_VALUE) {
                return value.longValue();
            }
        }
        Method method = method(remoteServiceClass, "getLongDef", String.class, long.class);
        if (method != null) {
            Object result = invokeGetterOrNull(method, remoteService, fieldName, Long.MIN_VALUE);
            if (result instanceof Number value) {
                return value.longValue() == Long.MIN_VALUE ? null : value.longValue();
            }
        }
        return getString(remoteServiceClass, remoteService, fieldName);
    }

    private Object invokeGetterOrNull(Method method, Object target, Object... args)
        throws IllegalAccessException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException | RuntimeException ex) {
            return null;
        }
    }

    private Object invoke(Class<?> type, Object target, String methodName, Class<?>[] parameterTypes, Object[] args)
        throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private void callRemoteService(Class<?> remoteServiceClass, Object remoteService)
        throws ReflectiveOperationException {
        for (Method method : remoteServiceClass.getMethods()) {
            if ("call".equals(method.getName()) && method.getParameterCount() == 1) {
                method.invoke(remoteService, new Object[] {null});
                return;
            }
        }
        throw new NoSuchMethodException(remoteServiceClass.getName() + ".call(Transaction)");
    }

    private Object applicationErrorService(Throwable error) {
        if (error == null) {
            return null;
        }
        Class<?> type = error.getClass();
        boolean applicationException = false;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if ("bea.jolt.ApplicationException".equals(current.getName())) {
                applicationException = true;
                break;
            }
        }
        if (!applicationException) {
            return null;
        }
        try {
            return type.getMethod("getObject").invoke(error);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private Method method(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private void endSession(Object session) {
        if (session == null) {
            return;
        }
        try {
            session.getClass().getMethod("endSession").invoke(session);
        } catch (ReflectiveOperationException ignored) {
            // Best effort cleanup for the optional runtime Jolt dependency.
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
