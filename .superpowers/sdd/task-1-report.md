# Task 1 Report: Project Scaffold And Reference Endpoints

## Implementation Summary

Task 1 delivered the Maven scaffold and the first Spring Boot slice for the CNAPS single-table POC under `web-war/`.

Implemented:
- Spring Boot entry point for package discovery.
- Shared `ApiResponse<T>` response wrapper with the required success/failure factory methods.
- `ErrorCode` and `BusinessException` for typed business failures.
- `GlobalExceptionHandler` for API-level exception translation.
- `ReferenceController` with:
  - `GET /api/health`
  - `GET /api/dicts/{dictType}`
  - `GET /api/banks`
- `application.yml` with the basic Spring datasource/JPA/H2 config.
- The Task 1 MockMvc test from the brief.

## TDD Evidence

### RED

Command:
```bash
mvn -f web-war/pom.xml test -Dtest=HealthAndReferenceApiTest
```

Expected failure observed:
- Spring test bootstrap failed because no `@SpringBootConfiguration` existed yet.
- Key message:
  - `Unable to find a @SpringBootConfiguration, you need to use @ContextConfiguration or @SpringBootTest(classes=...) with your test`

### GREEN

Command:
```bash
mvn -f web-war/pom.xml test -Dtest=HealthAndReferenceApiTest
```

Result:
- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

## Files Changed

- `web-war/pom.xml`
- `web-war/src/main/java/com/ruisui/bank/sim/RuisuiBankSimApplication.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/ApiResponse.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/GlobalExceptionHandler.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/ReferenceController.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/BusinessException.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/ErrorCode.java`
- `web-war/src/main/resources/application.yml`
- `web-war/src/test/java/com/ruisui/bank/sim/HealthAndReferenceApiTest.java`

## Self-Review

- The test file matches the brief exactly in behavior and assertions.
- The response wrapper matches the requested shape and factory methods.
- The health endpoint returns the required `UP` status and service list with `SYSHEALTH` first.
- The business dictionary and bank lookup return the exact reference values from the task brief.
- The code stays inside the task-owned `web-war` module and does not modify unrelated docs or later-task files.
- The Maven test run is clean after adding the surefire JVM flags that suppress the noisy agent/CDS warnings.

## Concerns

- None for Task 1 scope.
- The in-memory reference data is intentionally static for this POC phase and is ready to be replaced by later tasks.

## Important Fix Follow-Up

Reviewer finding fixed:
- Removed the `maven-surefire-plugin` `argLine` from `web-war/pom.xml` so the Maven build remains portable for the Java 17 baseline.

Verification command:
```bash
mvn -f web-war/pom.xml test -Dtest=HealthAndReferenceApiTest
```

Result:
- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

Note:
- The test still emits the standard Byte Buddy dynamic-agent warning from the test stack, but no Java 17-incompatible JVM flag is configured in the project anymore.
