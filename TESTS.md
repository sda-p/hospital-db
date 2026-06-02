# Test Infrastructure

This project uses Maven with JUnit 5 and the Surefire test runner.

## What Is Wired Up

- `pom.xml` declares `org.junit.jupiter:junit-jupiter` as the test framework.
- `pom.xml` uses `maven-surefire-plugin` to discover and run tests.
- Test sources live under `src/test/java`.
- The suite is split into two broad categories:
  - unit-style tests in `src/test/java/com/hospitalclaims/service`
  - integration and end-to-end tests in `src/test/java/com/hospitalclaims/integration`
- The test support code in `src/test/java/com/hospitalclaims/TestSupport.java` builds a sample in-memory service setup with fake repositories for isolated service and HTTP tests.
- The MySQL-backed integration tests extend `MysqlIntegrationTestSupport` and exercise the real repository layer and the menu-driven application flow against a live database.

## Coverage Shape

- Service-layer tests cover validation, claim-review logic, patient history reporting, update behavior, and search/query behavior.
- HTTP tests exercise the `HospitalClaimsHttpServer` through real requests.
- Integration tests verify repository persistence and the end-to-end console workflow against MySQL.

## Test Run

Command run:

```bash
mvn test
```

Outcome:

- `BUILD SUCCESS`
- `Tests run: 43`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

## Individual Test Components And Outcomes

| Test class | Type | Tests | Outcome |
| --- | --- | ---: | --- |
| `com.hospitalclaims.integration.AppEndToEndIntegrationTest` | Integration | 1 | Passed |
| `com.hospitalclaims.integration.RepositoryIntegrationTest` | Integration | 2 | Passed |
| `com.hospitalclaims.HospitalClaimsHttpServerTest` | HTTP / integration-style | 11 | Passed |
| `com.hospitalclaims.service.ValidationUtilsTest` | Unit | 8 | Passed |
| `com.hospitalclaims.service.ViewSearchServiceTest` | Unit | 9 | Passed |
| `com.hospitalclaims.service.HospitalClaimsServiceTest` | Unit | 12 | Passed |

## Notes

- The suite currently provides strong functional coverage, but there is no dedicated code-coverage tool or coverage threshold configured in the build.
- Several small domain and support classes are exercised indirectly rather than through dedicated unit tests.

## Fictional User Profiles

### 1. Claims Operations Analyst

Maya is a claims analyst who reviews submitted prescriptions, checks whether claim-review rules are satisfied, and exports results for follow-up. She uses the webUI to find patient-linked claims quickly, inspect eligibility issues, and confirm whether a record should move forward.

### 2. Front Desk Coordinator

Daniel works at a clinic front desk and updates patient contact details and primary-care links. He needs a simple workflow for searching a patient, editing a record inline, and verifying that the updated details appear immediately in the view workspace.

### 3. Provider Support Assistant

Priya supports doctors and pharmacy workflows by checking visits, prescriptions, and linked provider details. She uses the webUI to look up records by identifier, move between datasets, and confirm that related information is visible without needing the database directly.

## WebUI Usability Test Sequences

### 1. Review a Claim-Review Result Set

1. Open the webUI `View workspace`.
2. Select the `claim-review` dataset.
3. Enter the query `eligible`.
4. Run the search.
5. Confirm that a prescription row is shown with eligibility details.
6. Click the `claim-review` or prescription-linked identifier if available.
7. Verify that the result page shows the related record context and the expected eligibility information.

### 2. Edit a Patient Inline

1. Open the webUI `View workspace`.
2. Select the `patients` dataset.
3. Sort by `patientId:asc`.
4. Open the inline edit controls for a patient row.
5. Change the surname and contact fields.
6. Save the update.
7. Confirm that the page returns to the same view state.
8. Verify that the updated values are visible in the refreshed results.

### 3. Save and Reopen a Search

1. Open the webUI `View workspace`.
2. Select the `claims` dataset.
3. Enter the query `eligible`.
4. Add a grouping or sort option, such as grouping by `status`.
5. Save the search under a memorable name.
6. Reopen the saved search from the saved-search selector.
7. Confirm that the same dataset, query, grouping, and sort settings are restored.
8. Verify that the returned records match the original saved view.
