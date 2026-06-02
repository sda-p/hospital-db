# Hospital Claims Application Plan

## Goal
Develop a Java application for a hospital database used by a mid-size health insurance company to track health claims. The application must manage patient information, doctor/provider information, patient visits, prescribed drugs, and prescription history so the business can evaluate claim eligibility and later support trend analysis and extrapolative modelling. The system is English-only for now.

## Business Scope
- Track patient demographics and contact details:
  - name
  - address
  - phone
  - email
  - insurance ID
  - insurance company
- Track provider information:
  - doctor name
  - specialty
  - phone
  - address
  - email
  - hospital/clinic
- Track patient-to-doctor relationships:
  - patient visits
  - each patient's primary care doctor
- Track prescriptions:
  - which provider prescribed them
  - which patient received them
  - date, dosage, and duration
- Track drugs:
  - name
  - purpose/use
  - possible side effects
- Support future analytics by keeping the model and data access layer structured and extensible.

## Current State
- Maven project is set up and builds successfully.
- JDBC connectivity is implemented through externalized configuration in `src/main/resources/application.properties`.
- The SQL source dump in `hospital_new_new.sql` has been aligned to the stated business requirements by:
  - adding `Doctor.phone`
  - adding `Patient.primaryCareDoctorID`
  - renaming `Drug.benefits` to `Drug.purpose`
- Domain model classes exist for:
  - `Claim`
  - `Insurance`
  - `Patient`
  - `Doctor`
  - `Drug`
  - `Prescription`
  - `Visit`
- Repository classes exist for all seven tables and support save plus read operations.
- Repository classes now support update/edit operations for insurance, patients, doctors, drugs, visits, prescriptions, and claims.
- A service layer exists for validation and orchestration of save/retrieve/update/delete/claim workflows.
- The service layer now blocks duplicate overlapping prescriptions for the same patient and drug as a first-pass eligibility safeguard.
- The service layer now validates key reference relationships before create/record operations so patient, doctor, drug, and insurance links fail fast with clear workflow errors before JDBC foreign-key failures.
- The service layer now also produces richer claim-review eligibility results for each prescription, including:
  - missing insurance coverage on the patient record
  - no supporting visit with the prescribing doctor in the 30 days before the prescription date
  - prescriptions longer than 30 days that lack a clinical comment
  - overlapping same-drug prescriptions already present on file
- The application now supports richer retrieval workflows through the service layer:
  - patient history with linked insurance and primary care doctor context
  - visit history by patient
  - prescription review views with active/expired status, calculated end dates, and eligibility outcomes
  - case-insensitive fragment search for patients by surname, doctors by specialization, and drugs by name
- `App.java` now:
  - tests database connectivity
  - prints a live summary of loaded records
  - can optionally insert demo records via `create-demo-records`
  - supports guided menu-driven console flows when run without arguments or with `menu`
  - exposes interactive create/record commands for insurance providers, patients, doctors, drugs, visits, prescriptions, and claims
  - exposes interactive update commands for existing insurance, patient, doctor, drug, visit, and prescription records
  - exposes interactive delete commands with confirmation for insurance, patient, doctor, drug, visit, prescription, and claim records
  - exposes a lightweight HTTP interface when run with `serve`
  - still exposes argument-based commands for patient history, claim review, claim queue, and search workflows
- Automated verification now includes:
  - unit tests for validation helpers and service-layer claim-review/history/update logic
  - repository integration tests against a real MySQL instance using isolated test databases
  - a menu-driven end-to-end integration test that exercises create and update flows against MySQL through `App.main`
- The test suite passes with:
  - `mvn -Dmaven.repo.local=/private/tmp/codex-m2repo test`
- End-to-end verification was completed locally on May 8, 2026 against a real MySQL instance using the new menu/data-entry flows.

## Confirmed Database Schema
- Source dump: `hospital_new_new.sql`
- Confirmed tables:
  - `Claim`
  - `Insurance`
  - `Patient`
  - `Doctor`
  - `Drug`
  - `Prescription`
  - `Visit`
- Confirmed foreign keys:
  - `Claim.prescriptionID -> Prescription.prescriptionID`
  - `Claim.patientID -> Patient.patientID`
  - `Claim.insuranceID -> Insurance.insuranceID`
  - `Patient.insuranceID -> Insurance.insuranceID`
  - `Patient.primaryCareDoctorID -> Doctor.doctorID`
  - `Prescription.drugID -> Drug.drugID`
  - `Prescription.doctorID -> Doctor.doctorID`
  - `Prescription.patientID -> Patient.patientID`
  - `Visit.patientID -> Patient.patientID`
  - `Visit.doctorID -> Doctor.doctorID`

## Schema Gaps Against Business Requirements
- There is no explicit conflict-checking model for prescriptions.
- The claim entity now exists, but payment settlement, prior authorization, and richer rule-reference data are still out of scope.
- The app now has both console and HTTP interfaces, but the HTTP workflow is still intentionally lightweight.

## What Is Implemented vs Missing

### Implemented
1. Project skeleton, Maven build, and dependency management.
2. Database configuration and JDBC connection layer.
3. Models that map to the current SQL dump.
4. Repository layer for CRUD-style insert, retrieval, update, and delete access.
5. Service layer with validation, foreign-key reference checks, update workflows, delete safeguards, claim workflows, duplicate active-prescription checks, and patient/prescription review workflows.
6. Console flows for:
   - summary output
   - guided menu navigation
   - insurance/provider/patient/drug/visit/prescription data entry
   - insurance/patient/doctor/drug/visit/prescription update workflows
   - claim creation and approval workflows
   - delete workflows with confirmation
   - optional demo record insertion
   - patient history lookup
    - prescription claim review with eligibility reasons
   - claim queue review
   - patient/doctor/drug search
7. A lightweight HTTP interface for updates, deletes, and claim handling.
8. Automated coverage for validation, service rules, repository persistence, and menu-driven MySQL end-to-end flows.

### Missing
1. Claim payment/settlement is not modelled yet after approval.
2. The HTTP interface is intentionally lightweight and does not yet expose the full create/edit/query surface.
3. The production-style operator UX still lacks audit trails, pagination, and bulk workflows.

## Next Steps

### Phase 1: Align the Data Model to the Real Requirements
1. Backfill `Patient.primaryCareDoctorID` for seed or production data where a primary doctor is known.
2. Backfill `Doctor.phone` where provider contact numbers are available.
3. Decide whether prescription eligibility should remain a service-layer rule or gain dedicated schema support:
   - simple duplicate active-drug detection is now implemented in the service layer
   - known drug interaction table
   - contraindication metadata by patient or diagnosis

### Phase 2: Update the Application Layers
1. Extend repositories to support:
   - patient lookup with insurance and primary care doctor context
   - doctor lookup by specialty
   - visit and prescription history by patient
   - drug lookup by name
2. Extend the service layer with business workflows for:
   - registering a patient with insurer and primary care doctor
   - recording a visit
   - recording a prescription
   - checking richer prescription conflicts before save

Status:
- The retrieval/reporting portion of Phase 2 is implemented.
- Create/record workflows are implemented on top of the existing schema and repositories.
- Update workflows for patients, doctors, visits, and prescriptions are implemented.
- Claim-eligibility logic has moved beyond duplicate detection, but deeper clinical/business rules still remain possible future work.

### Phase 3: Improve the User Workflow
1. Replace or extend the current demo-style `App.java` with a clearer interactive or menu-driven console workflow.
2. Add actions for:
   - create patient
   - create doctor
   - record visit
   - record prescription
   - search patient history
   - view prescriptions for claim review
3. Make the output use insurance-company language rather than internal demo messages.

Status:
- Search patient history and prescription review commands now exist.
- A guided menu plus interactive create/record/update/delete/claim flows are implemented.
- Operator UX is still intentionally simple, but it is no longer console-only because a lightweight HTTP workflow now exists.

### Phase 3A: Close Current Interface Gaps
1. Insurance update workflow is exposed through both:
   - `update-insurance`
   - a dedicated menu option
2. Drug update workflow is exposed through both:
   - `update-drug`
   - a dedicated menu option
3. Delete workflows with confirmation now exist for:
   - insurance
   - patient
   - doctor
   - drug
   - visit
   - prescription
   - claim
4. Current delete behavior is implemented as:
   - hard delete only
   - blocked when dependent records still exist
   - confirmation text required before execution

Status:
- This phase is implemented in the console and the HTTP interface.

### Phase 3B: Move Beyond Console-Only Operation
1. Choose the next interface target:
   - web UI
   - desktop GUI
   - REST API with a thin frontend
2. Preserve the service layer as the business boundary so the current console remains a fallback/admin tool.
3. Support operator tasks that are awkward in the console:
   - multi-record browsing
   - paginated search
   - safer destructive actions
   - approval queues
   - audit visibility

Status:
- The application is no longer console-only because it now exposes a lightweight HTTP interface.
- The current console plus HTTP mix is suitable for demos and low-volume manual testing, but not yet for fuller operator workflows.

### Phase 3C: Introduce First-Class Claim Operations
1. Add a separate `Claim` entity rather than inferring claim handling solely from prescriptions.
2. Model the claim lifecycle explicitly, for example:
   - drafted
   - submitted
   - under review
   - approved
   - rejected
   - paid
3. Add a claim approval workflow that records:
   - reviewer/operator identity where available
   - approval decision
   - decision date
   - reason codes or notes
4. Link claims to the relevant clinical records:
   - patient
   - insurer
   - prescription and/or visit
5. Keep current prescription eligibility checks as supporting rules that feed into, but do not replace, claim approval.

Status:
- A separate claim entity now exists.
- A claim approval workflow now exists with draft, submitted, under-review, approved, and rejected states.
- Current claim review output remains the prescription-centric eligibility view that supports, rather than replaces, claim handling.

### Phase 4: Verification and Quality
1. Add repository integration tests against a real or test MySQL database.
2. Expand service-level tests for registration flows, search edge cases, and claim-review rules.
3. Verify the app against the imported `hospital_new_new.sql` dataset plus any schema migrations.
4. Confirm runtime execution locally once valid MySQL credentials are configured.

Status:
- Repository integration tests against real MySQL are implemented.
- Service-level tests now cover richer claim-review rules and update flows.
- Runtime execution has been verified locally through an end-to-end menu test against MySQL.
- Verification against the full imported `hospital_new_new.sql` seed dataset is still a useful follow-up, but the new flows are no longer unverified.

## Immediate Priority
The schema/business-model mismatch is closed, and the core retrieval, create, and update workflows are now implemented and verified against MySQL. The next implementation work should focus on:
1. expanding the HTTP interface beyond the new admin workflows into fuller day-to-day record management
2. deciding whether approved claims should continue into payment/settlement states
3. adding richer business rules such as prior authorization, interaction tables, and denial reason catalogues
4. verifying the new flows against the full imported `hospital_new_new.sql` seed dataset as a separate regression pass
