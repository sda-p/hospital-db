# Hospital Claims Interface

## Overview
The application exposes two operator interfaces through `com.hospitalclaims.App`:
- a console/menu workflow
- a lightweight HTTP workflow started with `serve`

The interface is database-backed. If the configured MySQL connection fails, the app exits after printing:

`Database connection failed. Check src/main/resources/application.properties.`

## Startup Modes

### Default Startup
Run:

```bash
mvn exec:java
```

Behavior:
- tests database connectivity
- prints a live record-count summary
- opens the interactive menu

### Explicit Menu Startup
Run:

```bash
mvn exec:java -Dexec.args="menu"
```

### Command Startup
Run:

```bash
mvn exec:java -Dexec.args="<command>"
```

Behavior:
- runs the selected workflow
- prints validation errors as `Validation failed: ...`
- prints SQL/runtime database failures as `Database operation failed: ...`

### HTTP Startup
Run:

```bash
mvn exec:java -Dexec.args="serve"
```

Optional port:

```bash
mvn exec:java -Dexec.args="serve 8080"
```

Behavior:
- starts a browser-accessible HTTP interface on `http://localhost:<port>`
- exposes separate browser views for:
  - dashboard summary
  - record creation and updates
  - claim actions and queue management
  - patient-history and claim-review workflows
  - dedicated record search and grouping workflows in the View workspace
  - delete workflows in an isolated destructive-actions view

## Summary Output
The summary view is shown at startup and is also available from the menu.

It prints:
- database connection status
- counts for insurance providers, patients, doctors, drugs, prescriptions, visits, and claims
- one sample patient if available
- one sample doctor if available
- one sample prescription if available
- one sample claim if available

## Command Interface

### General Commands
- `menu`
- `help`
- `serve [port]`
- `create-demo-records`

### Create Commands
- `create-insurance`
- `create-patient`
- `create-doctor`
- `create-drug`
- `record-visit`
- `record-prescription`
- `create-claim`

### Update Commands
- `update-insurance`
- `update-patient`
- `update-doctor`
- `update-drug`
- `update-visit`
- `update-prescription`

### Claim Commands
- `submit-claim`
- `review-claim`
- `approve-claim`
- `reject-claim`
- `claim-queue [patientId]`

### Delete Commands
- `delete-insurance`
- `delete-patient`
- `delete-doctor`
- `delete-drug`
- `delete-visit`
- `delete-prescription`
- `delete-claim`

### Query Commands
- `patient-history <patientId>`
- `claim-review [patientId]`
- `find-patients <surname-fragment>`
- `find-doctors <specialization-fragment>`
- `find-drugs <drug-name-fragment>`

## Interactive Menu
Current menu options:

1. View database summary
2. Register insurance provider
3. Update insurance provider
4. Register patient
5. Register doctor
6. Register drug
7. Update drug
8. Record visit
9. Record prescription
10. Update patient
11. Update doctor
12. Update visit
13. Update prescription
14. Create claim draft
15. Submit claim
16. Move claim to under review
17. Approve claim
18. Reject claim
19. View claim queue
20. Delete insurance provider
21. Delete patient
22. Delete doctor
23. Delete drug
24. Delete visit
25. Delete prescription
26. Delete claim
27. View patient history
28. View prescription claim review
29. Search patients by surname
30. Search doctors by specialization
31. Search drugs by name
32. Start browser interface
33. Exit

Also accepted for exit:
- `q`
- `quit`
- `exit`

## Data Entry Rules

### General Validation
- required text fields must not be blank
- email fields must look like valid email addresses
- date fields must use `YYYY-MM-DD`
- prescription duration must be a positive integer

### Reference Validation
The service validates linked records before saving:
- patient insurance must exist if supplied
- patient primary care doctor must exist if supplied
- visit patient and doctor must exist
- prescription patient, doctor, and drug must exist
- claim prescription must exist
- claim creation requires the linked patient to have insurance on file

### Update Flow Rules
- the target record must already exist
- entering a blank value keeps the existing value
- entering `-` clears an optional field
- required fields cannot be cleared

For `update-visit`, the operator first identifies the existing visit by:
- patient ID
- doctor ID
- visit date

### Delete Flow Rules
- delete flows require explicit confirmation text `DELETE`
- deletes are hard deletes
- deletes are blocked when dependent records still exist
- claims must be removed before deleting a linked prescription, patient, or insurance record

### Claim Workflow Rules
- claims are separate records rather than inferred prescription rows
- each claim links to one prescription
- claims move through `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, and `REJECTED`
- approval and rejection require reviewer information

## Workflow Details

### Browser Views
- `/` dashboard with operational summary cards and workflow navigation
- `/records` create and update forms for insurance, patient, doctor, drug, visit, and prescription workflows
- `/claims` claim draft, submit, review, approve, reject, and queue filter workflows
- `/reviews` patient history, prescription claim review, patient search, doctor search, and drug search workflows
- `/view` shared record-search workspace with patient, doctor, drug, visit, prescription, claim, and claim-review datasets plus flag/value filters, wildcard and regex matching, sorting, grouping, pagination, saved searches, CSV/JSON exports, and inline row editing for patients/doctors/drugs/visits/prescriptions
- `/delete` destructive delete forms separated from routine workflows
- all form actions redirect back to their owning view with success or validation/database error messages

### View Workspace Inline Editing
- result rows for `patients`, `doctors`, `drugs`, `visits`, and `prescriptions` include an inline `Edit row` control
- inline edits post to a `/actions/view-inline-edit` HTTP action
- the operator is returned to the same `/view` dataset, filter, sort, group, page, and page-size state after save
- `claims` and `claim-review` remain read-only in `/view`
- inline visit edits preserve the original visit identity through `originalPatientId`, `originalDoctorId`, and `originalDateOfVisit`

### `claim-review [patientId]`
Prints prescription eligibility rows for all prescriptions or one patient.

Each row includes:
- prescription ID
- patient ID
- drug
- prescriber
- start date
- end date
- active status
- eligibility status
- dosage
- eligibility issues

### `claim-queue [patientId]`
Prints claim rows for all claims or one patient.

Each row includes:
- claim ID
- patient ID
- prescription ID
- insurance ID
- claim status
- created date
- submitted date
- reviewer
- decision date
- linked prescription eligibility status
- linked prescription eligibility issues

## Runtime Configuration
Database configuration is read from:
- `src/main/resources/application.properties`

The app also accepts overrides through:
- JVM system properties: `db.url`, `db.username`, `db.password`
- environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## Current Interface Limits
- the HTTP interface is intentionally lightweight and focuses on updates, deletes, and claim handling rather than the full create/edit surface
- the `/view` workspace now includes limited inline editing for directly updatable record datasets, but claim workflows still remain separate from result-grid editing
- saved `/view` searches are stored in a local `view-saved-searches.properties` file rather than a database table
- delete behavior is hard-delete only; there is no soft-delete or archive model yet
- claim approval does not yet model payment settlement or prior authorization
