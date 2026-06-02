# Hospital Claims Application

Java object-oriented assessment project for a hospital claims. The project is set up to connect to a MySQL database created from `hospital_new_new.sql`.

## Prerequisites
- JDK 21
- Maven 3.9+
- MySQL client/server access

## Local Setup
1. Install the toolchain on macOS Apple Silicon:
   - `brew install openjdk@21 maven`
2. Load the provided environment helper for the current shell:
   - `source scripts/dev-env.zsh`
3. Copy the local database properties template if needed and update credentials:
   - `src/main/resources/application.properties`
   - You can also override `db.url`, `db.username`, and `db.password` at runtime with JVM system properties or `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
4. Create and load the database:
   - `mysql -u <user> -p -e "CREATE DATABASE IF NOT EXISTS hospital_new;"`
   - `mysql -u <user> -p hospital_new < hospital_new_new.sql`
   - Re-import the dump if you previously loaded an older version, because the schema now includes `Doctor.phone`, `Patient.primaryCareDoctorID`, and `Drug.purpose`.
5. Run the application summary:
   - `mvn exec:java`
6. Optionally create demo records after configuring valid database credentials:
   - `mvn exec:java -Dexec.args="create-demo-records"`
7. Run a patient-history lookup:
   - `mvn exec:java -Dexec.args="patient-history <patientId>"`
8. Run prescription claim review:
   - `mvn exec:java -Dexec.args="claim-review"`
   - `mvn exec:java -Dexec.args="claim-review <patientId>"`
9. Search by patient, doctor, or drug:
   - `mvn exec:java -Dexec.args="find-patients <surname-fragment>"`
   - `mvn exec:java -Dexec.args="find-doctors <specialization-fragment>"`
   - `mvn exec:java -Dexec.args="find-drugs <drug-name-fragment>"`
10. Update existing records:
   - `mvn exec:java -Dexec.args="update-insurance"`
   - `mvn exec:java -Dexec.args="update-patient"`
   - `mvn exec:java -Dexec.args="update-doctor"`
   - `mvn exec:java -Dexec.args="update-drug"`
   - `mvn exec:java -Dexec.args="update-visit"`
   - `mvn exec:java -Dexec.args="update-prescription"`
11. Run claim workflows:
   - `mvn exec:java -Dexec.args="create-claim"`
   - `mvn exec:java -Dexec.args="submit-claim"`
   - `mvn exec:java -Dexec.args="review-claim"`
   - `mvn exec:java -Dexec.args="approve-claim"`
   - `mvn exec:java -Dexec.args="reject-claim"`
   - `mvn exec:java -Dexec.args="claim-queue"`
12. Run delete workflows:
   - `mvn exec:java -Dexec.args="delete-insurance"`
   - `mvn exec:java -Dexec.args="delete-patient"`
   - `mvn exec:java -Dexec.args="delete-doctor"`
   - `mvn exec:java -Dexec.args="delete-drug"`
   - `mvn exec:java -Dexec.args="delete-visit"`
   - `mvn exec:java -Dexec.args="delete-prescription"`
   - `mvn exec:java -Dexec.args="delete-claim"`
13. Run the guided menu:
   - `mvn exec:java -Dexec.args="menu"`
14. Start the browser-accessible interface:
   - `mvn exec:java -Dexec.args="serve"`
15. Run tests:
   - `mvn test`

## Current Functionality
- Loads database configuration from `src/main/resources/application.properties`
- Accepts database config overrides from JVM system properties or environment variables
- Connects to MySQL using JDBC
- Provides model, repository, and service layers for:
  - `Claim`
  - `Insurance`
  - `Patient`
  - `Doctor`
  - `Drug`
  - `Prescription`
  - `Visit`
- Prints a read-only database summary by default
- Can insert a small set of demo records when run with `create-demo-records`
- Supports interactive create workflows for insurance, patients, doctors, drugs, visits, prescriptions, and claims
- Supports interactive update workflows for insurance, patients, doctors, drugs, visits, and prescriptions
- Supports interactive delete workflows with confirmation for insurance, patients, doctors, drugs, visits, prescriptions, and claims
- Supports a lightweight HTTP interface in addition to the console/menu workflow
- Supports a dedicated `/view` WebUI workspace for shared record retrieval with:
  - dataset selection
  - patients, doctors, drugs, visits, prescriptions, claims, and claim-review datasets
  - flag and value-based filtering
  - wildcard `*` matching
  - regex matching
  - sorting and grouping
  - pagination
  - inline row editing for patients, doctors, drugs, visits, and prescriptions
  - read-only derived/result datasets for claims and claim-review
  - saved searches persisted to `view-saved-searches.properties`
  - CSV and JSON export links for the active search
- Can print a patient history report with linked insurance, primary care doctor, visits, prescriptions, and claim-review issues
- Can print prescription claim-review views with active/expired status plus eligibility checks for:
  - overlapping same-drug prescriptions
  - missing insurance coverage
  - missing supporting visit with the prescriber in the prior 30 days
  - long durations without a clinical comment
- Can print a claim queue with claim status plus the linked prescription eligibility outcome
- Can search for patients, doctors, and drugs with case-insensitive fragment matching
- Includes MySQL-backed repository integration tests and a full menu-driven end-to-end integration test

## Project Layout
- `src/main/java/com/hospitalclaims/App.java`
- `src/main/java/com/hospitalclaims/config`
- `src/main/java/com/hospitalclaims/db`
- `src/main/java/com/hospitalclaims/model`
- `src/main/java/com/hospitalclaims/repository`
- `src/main/java/com/hospitalclaims/service`
- `src/test/java/com/hospitalclaims/service`
- `src/test/java/com/hospitalclaims/integration`
- `src/main/resources/application.properties`
- `scripts/dev-env.zsh`
