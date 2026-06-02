# Hospital Claims Schema

## Overview
The application currently uses seven core tables:

1. `Insurance`
2. `Patient`
3. `Doctor`
4. `Drug`
5. `Prescription`
6. `Visit`
7. `Claim`

The source-of-truth SQL is [hospital_new_new.sql](/Users/cris/Documents/college/assessments/software_assessment/hospital_new_new.sql).

## Current Schema

### Insurance
Stores insurer/provider details linked from patients.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `insuranceID` | `varchar(20)` | No | Primary key |
| `company` | `varchar(50)` | Yes | Insurance company name |
| `address` | `varchar(100)` | Yes | Postal address |
| `phone` | `varchar(20)` | Yes | Contact phone |

### Doctor
Stores provider details.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `doctorID` | `varchar(20)` | No | Primary key |
| `firstname` | `varchar(20)` | Yes | Given name |
| `surname` | `varchar(20)` | Yes | Family name |
| `address` | `varchar(100)` | Yes | Postal address |
| `phone` | `varchar(20)` | Yes | Provider phone number |
| `email` | `varchar(50)` | Yes | Provider email |
| `specialization` | `varchar(50)` | Yes | Specialty |
| `hospital` | `varchar(100)` | Yes | Hospital or clinic |

### Drug
Stores medication reference data.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `drugID` | `varchar(20)` | No | Primary key |
| `drugname` | `varchar(50)` | Yes | Drug name |
| `sideeffects` | `text` | Yes | Possible side effects |
| `purpose` | `text` | Yes | Purpose/use of the drug |

### Patient
Stores patient demographics, insurer link, and primary-care assignment.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `patientID` | `varchar(20)` | No | Primary key |
| `firstname` | `varchar(20)` | Yes | Given name |
| `surname` | `varchar(30)` | Yes | Family name |
| `postcode` | `varchar(20)` | Yes | Postal code |
| `address` | `varchar(100)` | Yes | Postal address |
| `phone` | `varchar(20)` | Yes | Patient phone |
| `email` | `varchar(50)` | Yes | Patient email |
| `insuranceID` | `varchar(20)` | Yes | FK to `Insurance.insuranceID` |
| `primaryCareDoctorID` | `varchar(20)` | Yes | FK to `Doctor.doctorID` |

### Prescription
Stores prescriptions issued to patients.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `prescriptionID` | `varchar(20)` | No | Primary key |
| `dateprescribed` | `date` | Yes | Prescription date |
| `dosage` | `varchar(100)` | Yes | Dosage text/value |
| `duration` | `varchar(50)` | Yes | Duration in days, currently validated in the Java layer as a positive integer |
| `comment` | `varchar(255)` | Yes | Free-text note |
| `drugID` | `varchar(20)` | Yes | FK to `Drug.drugID` |
| `doctorID` | `varchar(20)` | Yes | FK to `Doctor.doctorID` |
| `patientID` | `varchar(20)` | Yes | FK to `Patient.patientID` |

### Visit
Stores patient visits with providers.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `patientID` | `varchar(20)` | No | Part of composite PK, FK to `Patient.patientID` |
| `doctorID` | `varchar(20)` | No | Part of composite PK, FK to `Doctor.doctorID` |
| `dateofvisit` | `date` | No | Part of composite PK |
| `symptoms` | `varchar(255)` | Yes | Visit symptoms or reason |
| `diagnosisID` | `varchar(255)` | Yes | Diagnosis reference/code |

### Claim
Stores claim workflow records linked to prescriptions, patients, and insurers.

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `claimID` | `varchar(20)` | No | Primary key |
| `prescriptionID` | `varchar(20)` | No | Unique FK to `Prescription.prescriptionID` |
| `patientID` | `varchar(20)` | No | FK to `Patient.patientID` |
| `insuranceID` | `varchar(20)` | No | FK to `Insurance.insuranceID` |
| `status` | `varchar(20)` | No | Claim workflow status |
| `createdDate` | `date` | No | Claim creation date |
| `submittedDate` | `date` | Yes | Date moved to submitted state |
| `reviewedBy` | `varchar(100)` | Yes | Reviewer identifier |
| `decisionDate` | `date` | Yes | Approval or rejection date |
| `decisionNotes` | `varchar(255)` | Yes | Reviewer notes |

## Keys And Relationships

### Primary Keys
- `Insurance(insuranceID)`
- `Doctor(doctorID)`
- `Drug(drugID)`
- `Patient(patientID)`
- `Prescription(prescriptionID)`
- `Visit(patientID, doctorID, dateofvisit)`
- `Claim(claimID)`

### Unique Keys
- `Claim(prescriptionID)`

### Foreign Keys
- `Patient.insuranceID -> Insurance.insuranceID`
- `Patient.primaryCareDoctorID -> Doctor.doctorID`
- `Prescription.drugID -> Drug.drugID`
- `Prescription.doctorID -> Doctor.doctorID`
- `Prescription.patientID -> Patient.patientID`
- `Visit.patientID -> Patient.patientID`
- `Visit.doctorID -> Doctor.doctorID`
- `Claim.prescriptionID -> Prescription.prescriptionID`
- `Claim.patientID -> Patient.patientID`
- `Claim.insuranceID -> Insurance.insuranceID`

## Changes Made To Match The Stated Requirements
The original dump did not fully match the business requirements in [PLAN.md](/Users/cris/Documents/college/assessments/software_assessment/PLAN.md). These schema changes were made:

1. Added `Doctor.phone`.
Reason: provider information in the requirements includes doctor phone numbers.

2. Added `Patient.primaryCareDoctorID` with a foreign key to `Doctor.doctorID`.
Reason: the requirements call for each patient’s primary care doctor to be tracked.

3. Renamed `Drug.benefits` to `Drug.purpose`.
Reason: the requirements describe storing the drug’s purpose/use, and the Java model now uses that language consistently.

4. Updated seed inserts to name columns explicitly.
Reason: this keeps the existing sample data loadable even though the schema now has new nullable columns.

## Notes And Remaining Gaps
- No physical schema changes were made in this implementation pass.
- The new guided menu and create/record workflows use the existing seven-table schema without introducing new tables, columns, keys, or constraints.
- Application-level workflow validation now checks referenced `Insurance`, `Doctor`, `Drug`, and `Patient` records before insert operations, but those checks are still enforced in Java rather than through any new schema object.
- The new patient-history, claim-review, `visits` View dataset, and `claim-review` View dataset are built from the existing schema; no new tables, columns, keys, or constraints were introduced.
- View-workspace pagination, saved searches, CSV/JSON exports, and inline row editing are implemented entirely in the Java HTTP layer and do not add new schema objects.
- Existing seed rows are not backfilled with doctor phone numbers or primary-care doctor assignments; those new columns load as `NULL` unless explicitly populated later.
- Prescription conflict checking is currently implemented in the Java service layer, not as a database constraint or dedicated interaction table.
- The shared `/view` workspace exposes editable base-record datasets such as `patients`, `doctors`, `drugs`, `visits`, and `prescriptions` through HTTP-layer update flows without schema changes.
- The shared `/view` workspace exposes derived datasets such as `claim-review`, plus claim queue projections, as read-only projections over existing tables rather than new schema objects.
