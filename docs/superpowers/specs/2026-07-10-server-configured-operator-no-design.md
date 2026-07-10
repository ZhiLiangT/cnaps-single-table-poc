# Server-Configured Operator Number Design

**Date:** 2026-07-10

## Goal

Remove `operatorNo` from the public frontend HTTP contract while preserving the internal `OPERATOR_NO` business field used by WebFE, Tuxedo, and Oracle. Until authentication is introduced, WebFE supplies a fixed operator number from server-side runtime configuration.

## Scope

This change covers WebFE runtime configuration, WebFE-to-Tuxedo request construction, Tomcat deployment configuration, automated tests, and `docs/cnaps-frontend-api.md`.

It does not change the Oracle schema, Jolt metadata, Tuxedo field names, or C service persistence logic. It also does not introduce authentication or maker-checker identity enforcement.

## Runtime Configuration

The fixed POC operator number uses the existing WebFE runtime configuration precedence:

1. JVM system property: `webfe.poc.operatorNo`
2. Environment variable: `POC_OPERATOR_NO`
3. Application properties: `webfe.poc.operatorNo`
4. Servlet context parameter: `poc.operatorNo`
5. Built-in default: `77210021`

Blank values are ignored in the same way as existing Tuxedo runtime settings. `conf/app.properties` documents the default value, and the Tomcat configuration script persists `POC_OPERATOR_NO` for the deployed service.

## WebFE Design

`TuxedoRuntimeConfig` gains a `pocOperatorNo` value and resolves it with the same mechanism as the current mode, Jolt endpoint, timeout, and credential settings. `TuxedoClientProvider` exposes the resolved runtime configuration through the servlet context so the Tuxedo client and API servlets use one consistent configuration instance.

`BaseJsonServlet` reads the configured operator number during servlet initialization. For every Tuxedo call it passes that value to `TuxedoRequestMapper`. It no longer reads `operatorNo` from `HttpServletRequest`.

`RequestSupport.operatorNo(HttpServletRequest)` and its header-specific tests are removed. The remaining request context fields keep their current behavior:

- `requestId` remains caller-overridable and server-generated when absent.
- `branchNo` remains an optional POC request header.
- `workDate` remains an optional POC request header.

The Tuxedo request continues to contain `OPERATOR_NO`; therefore create records still populate `OPERATOR_NO`, while update, delete, and review operations continue to populate their existing operator audit fields.

## Data Flow

At application startup, WebFE resolves `pocOperatorNo` from server-controlled configuration and caches the runtime configuration in the servlet context. At request time, `BaseJsonServlet` combines the configured operator number with the request ID, branch number, work date, and endpoint fields. `TuxedoRequestMapper` maps the operator value to `OPERATOR_NO`, after which the existing Jolt/Tuxedo/Oracle flow is unchanged.

An incoming HTTP header named `operatorNo` has no effect on the Tuxedo request.

## Error Handling

If all configured values are missing or blank, WebFE uses `77210021`. No new request-level validation error is introduced because the caller no longer controls this value.

This change does not add format validation for the server-side value. Operational configuration remains responsible for supplying an operator number compatible with the Oracle `VARCHAR2(16)` field.

## API Documentation

`docs/cnaps-frontend-api.md` removes `operatorNo` from the public request-header table and from curl examples. A server-context note explains that WebFE supplies the operator number from `webfe.poc.operatorNo` / `POC_OPERATOR_NO`, defaults it to `77210021`, and continues forwarding it internally to Tuxedo.

## Testing

Automated tests verify:

- `pocOperatorNo` follows system property, environment, application properties, servlet context, and default precedence.
- The default operator number is `77210021`.
- Request support no longer treats `operatorNo` as a caller-controlled header.
- Existing Tuxedo request mapping still maps the server-provided value to `OPERATOR_NO`.
- The complete WebFE Maven test suite passes.

## Future Authentication Integration

When authentication is added, only the WebFE source of the operator number changes: `BaseJsonServlet` (or a dedicated authenticated request-context component introduced at that time) will obtain it from the verified identity. The downstream `TuxedoRequestMapper`, Jolt metadata, C services, and Oracle audit fields remain unchanged.
