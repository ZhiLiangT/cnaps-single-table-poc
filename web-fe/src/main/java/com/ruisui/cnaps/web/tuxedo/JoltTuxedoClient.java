package com.ruisui.cnaps.web.tuxedo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JoltTuxedoClient implements TuxedoClient {
    private static final List<String> RESPONSE_FIELDS = List.of(
        "RESP_CODE", "RESP_MSG", "WEBFE", "TUXEDO", "ORACLE", "SERVICE", "CHECK_TIME",
        "DICT_TYPE", "DICT_CODE", "DICT_NAME", "SORT_NO", "BANK_NO", "BANK_NAME",
        "BILL_ID", "SERIAL_NO", "WORK_DATE", "BUSINESS_TYPE", "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3",
        "ACCOUNT_NAME", "PAYER_NAME", "PAYEE_ACCT", "PAYEE_NAME", "PRIORITY", "RECEIVE_BANK_NO",
        "RECEIVE_BANK_NAME", "SYSTEM_TYPE", "AMOUNT", "DEBIT_MODE", "FEE_AMOUNT", "FEE_CHARGE_MODE",
        "SEND_MODE", "FAX_FLAG", "VOUCHER_NO", "REMARK", "STATUS", "REJECT_REASON", "REVIEW_COMMENT",
        "DELETE_REASON", "DELETE_OPERATOR_NO", "CHECKER_NO", "CHECKER_TIME", "LAST_ACTION", "INCLUDE_DELETED",
        "LAST_OPERATOR_NO", "LAST_REQUEST_ID", "PAGE_NO", "PAGE_SIZE", "TOTAL_ELEMENTS", "TOTAL_PAGES"
    );
    private static final List<String> NUMERIC_FIELDS = List.of("PAGE_NO", "PAGE_SIZE", "TOTAL_ELEMENTS", "TOTAL_PAGES", "SORT_NO");

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
            setJoltAttribute(attributesClass, attributes, "RECVTIMEOUT", String.valueOf(config.joltTimeoutMillis()));

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
                putField(remoteServiceClass, remoteService, field.getKey(), field.getValue());
            }
            callRemoteService(remoteServiceClass, remoteService);

            Map<String, Object> fields = readResponseFields(remoteServiceClass, remoteService);
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
        if (NUMERIC_FIELDS.contains(name) && isInteger(text)) {
            Method setInt = method(remoteServiceClass, "setInt", String.class, int.class);
            if (setInt != null) {
                setInt.invoke(remoteService, name, Integer.parseInt(text));
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

    private Map<String, Object> readResponseFields(Class<?> remoteServiceClass, Object remoteService)
        throws ReflectiveOperationException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String fieldName : RESPONSE_FIELDS) {
            Object value = NUMERIC_FIELDS.contains(fieldName)
                ? getInt(remoteServiceClass, remoteService, fieldName)
                : getString(remoteServiceClass, remoteService, fieldName);
            if (value != null && !"".equals(value)) {
                fields.put(fieldName, value);
            }
        }
        return fields;
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

    private Object getInt(Class<?> remoteServiceClass, Object remoteService, String fieldName)
        throws ReflectiveOperationException {
        Method method = method(remoteServiceClass, "getIntDef", String.class, int.class);
        if (method != null) {
            Object result = invokeGetterOrNull(method, remoteService, fieldName, Integer.MIN_VALUE);
            if (result instanceof Integer value) {
                return value == Integer.MIN_VALUE ? null : value;
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
