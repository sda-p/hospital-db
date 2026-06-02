package com.hospitalclaims;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Claim;
import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Drug;
import com.hospitalclaims.model.Insurance;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Prescription;
import com.hospitalclaims.model.Visit;
import com.hospitalclaims.repository.ClaimRepository;
import com.hospitalclaims.repository.DoctorRepository;
import com.hospitalclaims.repository.DrugRepository;
import com.hospitalclaims.repository.InsuranceRepository;
import com.hospitalclaims.repository.PatientRepository;
import com.hospitalclaims.repository.PrescriptionRepository;
import com.hospitalclaims.repository.VisitRepository;
import com.hospitalclaims.service.ClaimView;
import com.hospitalclaims.service.HospitalClaimsService;
import com.hospitalclaims.service.PatientHistoryReport;
import com.hospitalclaims.service.PrescriptionReview;
import com.hospitalclaims.service.ValidationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

/** Console entry point for record management, claim workflows, and the HTTP UI. */
public class App {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Boots the application, verifies connectivity, and dispatches the chosen interface. */
    public static void main(String[] args) {
        DatabaseConnection connection = new DatabaseConnection();
        try (var ignored = connection.open()) {
            // Verify database settings before constructing repositories.
        } catch (SQLException exception) {
            System.out.println("Database connection failed. Check src/main/resources/application.properties.");
            System.out.println("JDBC error: " + exception.getMessage());
            return;
        }

        HospitalClaimsService service = createService(connection);

        try {
            if (args.length == 0) {
                runInteractiveConsole(service);
            } else {
                runCommand(service, args);
            }
        } catch (SQLException exception) {
            System.out.println("Database operation failed: " + exception.getMessage());
        } catch (ValidationException exception) {
            System.out.println("Validation failed: " + exception.getMessage());
        }
    }

    /** Wires repositories into the shared service layer. */
    private static HospitalClaimsService createService(DatabaseConnection connection) {
        return new HospitalClaimsService(
                new ClaimRepository(connection),
                new InsuranceRepository(connection),
                new PatientRepository(connection),
                new DoctorRepository(connection),
                new DrugRepository(connection),
                new PrescriptionRepository(connection),
                new VisitRepository(connection)
        );
    }

    /** Dispatches a single CLI command or falls back to usage information. */
    private static void runCommand(HospitalClaimsService service, String[] args) throws SQLException {
        if (args.length == 0) {
            printSummary(service);
            printUsage();
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "create-demo-records" -> createDemoRecords(service);
            case "create-insurance" -> createInsurance(service);
            case "update-insurance" -> updateInsurance(service);
            case "create-patient" -> createPatient(service);
            case "create-doctor" -> createDoctor(service);
            case "create-drug" -> createDrug(service);
            case "update-drug" -> updateDrug(service);
            case "record-visit" -> recordVisit(service);
            case "record-prescription" -> recordPrescription(service);
            case "update-patient" -> updatePatient(service);
            case "update-doctor" -> updateDoctor(service);
            case "update-visit" -> updateVisit(service);
            case "update-prescription" -> updatePrescription(service);
            case "delete-insurance" -> deleteInsurance(service);
            case "delete-patient" -> deletePatient(service);
            case "delete-doctor" -> deleteDoctor(service);
            case "delete-drug" -> deleteDrug(service);
            case "delete-visit" -> deleteVisit(service);
            case "delete-prescription" -> deletePrescription(service);
            case "create-claim" -> createClaim(service);
            case "submit-claim" -> submitClaim(service);
            case "review-claim" -> reviewClaim(service);
            case "approve-claim" -> approveClaim(service);
            case "reject-claim" -> rejectClaim(service);
            case "delete-claim" -> deleteClaim(service);
            case "claim-queue" -> printClaims(service, args);
            case "patient-history" -> printPatientHistory(service, requireArgument(args, 1, "patient-history <patientId>"));
            case "claim-review" -> printClaimReview(service, args);
            case "find-patients" -> printPatientsBySurname(service, joinArgs(args, 1));
            case "find-doctors" -> printDoctorsBySpecialization(service, joinArgs(args, 1));
            case "find-drugs" -> printDrugsByName(service, joinArgs(args, 1));
            case "serve" -> startHttpServer(service, args);
            case "menu" -> runInteractiveConsole(service);
            case "help" -> printUsage();
            default -> {
                System.out.println("Unknown command: " + args[0]);
                printUsage();
            }
        }
    }

    /** Prints a quick operational snapshot of the connected data set. */
    private static void printSummary(HospitalClaimsService service) throws SQLException {
        List<Insurance> providers = service.getAllInsuranceProviders();
        List<Patient> patients = service.getAllPatients();
        List<Doctor> doctors = service.getAllDoctors();
        List<Drug> drugs = service.getAllDrugs();
        List<Prescription> prescriptions = service.getAllPrescriptions();
        List<Visit> visits = service.getAllVisits();
        List<Claim> claims = service.getAllClaims();

        System.out.println("Database connection established.");
        System.out.println("Insurance providers loaded: " + providers.size());
        System.out.println("Patients loaded: " + patients.size());
        System.out.println("Doctors loaded: " + doctors.size());
        System.out.println("Drugs loaded: " + drugs.size());
        System.out.println("Prescriptions loaded: " + prescriptions.size());
        System.out.println("Visits loaded: " + visits.size());
        System.out.println("Claims loaded: " + claims.size());

        patients.stream().findFirst()
                .ifPresent(patient -> System.out.println("Sample patient: "
                        + patient.getPatientId() + " - " + patient.getFirstName() + " " + patient.getSurname()));

        doctors.stream().findFirst()
                .ifPresent(doctor -> System.out.println("Sample doctor: "
                        + doctor.getDoctorId() + " - " + doctor.getFirstName() + " " + doctor.getSurname()));

        prescriptions.stream().findFirst()
                .ifPresent(prescription -> System.out.println("Most recent prescription: "
                        + prescription.getPrescriptionId() + " for patient " + prescription.getPatientId()));

        claims.stream().findFirst()
                .ifPresent(claim -> System.out.println("Most recent claim: "
                        + claim.getClaimId() + " | status " + claim.getStatus()));
    }

    /** Prints a patient-centric report that joins visits, coverage, and prescriptions. */
    private static void printPatientHistory(HospitalClaimsService service, String patientId) throws SQLException {
        Optional<PatientHistoryReport> report = service.getPatientHistoryReport(patientId);
        if (report.isEmpty()) {
            System.out.println("No patient found for ID " + patientId + ".");
            return;
        }

        Patient patient = report.get().patient();
        System.out.println("Patient history for " + patient.getPatientId());
        System.out.println("Name: " + patient.getFirstName() + " " + patient.getSurname());
        System.out.println("Email: " + valueOrUnknown(patient.getEmail()));
        System.out.println("Phone: " + valueOrUnknown(patient.getPhone()));
        System.out.println("Insurance: " + formatInsurance(report.get().insurance(), patient.getInsuranceId()));
        System.out.println("Primary care doctor: " + formatDoctor(report.get().primaryCareDoctor(), patient.getPrimaryCareDoctorId()));

        System.out.println("Visits: " + report.get().visits().size());
        for (Visit visit : report.get().visits()) {
            System.out.println("  - " + DATE_FORMAT.format(visit.getDateOfVisit())
                    + " with doctor " + visit.getDoctorId()
                    + " | diagnosis " + valueOrUnknown(visit.getDiagnosisId())
                    + " | symptoms " + valueOrUnknown(visit.getSymptoms()));
        }

        System.out.println("Prescriptions: " + report.get().prescriptions().size());
        for (PrescriptionReview review : report.get().prescriptions()) {
            System.out.println("  - " + review.prescription().getPrescriptionId()
                    + " | " + DATE_FORMAT.format(review.prescription().getDatePrescribed())
                    + " | drug " + formatDrug(review)
                    + " | doctor " + formatDoctor(review.doctor(), review.prescription().getDoctorId())
                    + " | active " + (review.active() ? "yes" : "no")
                    + " | eligible " + (review.eligible() ? "yes" : "no")
                    + " | ends " + DATE_FORMAT.format(review.endDate())
                    + " | issues " + formatIssues(review.eligibilityIssues()));
        }
    }

    /** Prints prescription review output for all patients or one patient. */
    private static void printClaimReview(HospitalClaimsService service, String[] args) throws SQLException {
        List<PrescriptionReview> reviews = args.length > 1
                ? service.getPrescriptionReviewsForPatient(args[1])
                : service.getPrescriptionReviews();

        if (reviews.isEmpty()) {
            System.out.println("No prescriptions found for claim review.");
            return;
        }

        System.out.println("Prescription claim review items: " + reviews.size());
        for (PrescriptionReview review : reviews) {
            System.out.println(review.prescription().getPrescriptionId()
                    + " | patient " + review.prescription().getPatientId()
                    + " | " + formatDrug(review)
                    + " | prescriber " + formatDoctor(review.doctor(), review.prescription().getDoctorId())
                    + " | start " + DATE_FORMAT.format(review.prescription().getDatePrescribed())
                    + " | end " + DATE_FORMAT.format(review.endDate())
                    + " | active " + (review.active() ? "yes" : "no")
                    + " | eligible " + (review.eligible() ? "yes" : "no")
                    + " | dosage " + review.prescription().getDosage()
                    + " | issues " + formatIssues(review.eligibilityIssues()));
        }
    }

    /** Prints claims, optionally filtered down to one patient. */
    private static void printClaims(HospitalClaimsService service, String[] args) throws SQLException {
        List<ClaimView> claims = args.length > 1
                ? service.getClaimViewsForPatient(args[1])
                : service.getClaimViews();

        if (claims.isEmpty()) {
            System.out.println("No claims found.");
            return;
        }

        System.out.println("Claims found: " + claims.size());
        for (ClaimView view : claims) {
            Claim claim = view.claim();
            System.out.println(claim.getClaimId()
                    + " | patient " + claim.getPatientId()
                    + " | prescription " + claim.getPrescriptionId()
                    + " | insurance " + claim.getInsuranceId()
                    + " | status " + claim.getStatus()
                    + " | created " + DATE_FORMAT.format(claim.getCreatedDate())
                    + " | submitted " + formatDate(claim.getSubmittedDate())
                    + " | reviewer " + valueOrUnknown(claim.getReviewedBy())
                    + " | decision " + formatDate(claim.getDecisionDate())
                    + " | prescription eligible " + (view.prescriptionReview().eligible() ? "yes" : "no")
                    + " | issues " + formatIssues(view.prescriptionReview().eligibilityIssues()));
        }
    }

    private static void printPatientsBySurname(HospitalClaimsService service, String surname) throws SQLException {
        List<Patient> patients = service.findPatientsBySurname(surname);
        if (patients.isEmpty()) {
            System.out.println("No patients found matching surname: " + surname);
            return;
        }

        System.out.println("Patients found: " + patients.size());
        for (Patient patient : patients) {
            System.out.println(patient.getPatientId()
                    + " | " + patient.getFirstName() + " " + patient.getSurname()
                    + " | insurance " + valueOrUnknown(patient.getInsuranceId())
                    + " | primary care doctor " + valueOrUnknown(patient.getPrimaryCareDoctorId()));
        }
    }

    private static void printDoctorsBySpecialization(HospitalClaimsService service, String specialization) throws SQLException {
        List<Doctor> doctors = service.findDoctorsBySpecialization(specialization);
        if (doctors.isEmpty()) {
            System.out.println("No doctors found matching specialization: " + specialization);
            return;
        }

        System.out.println("Doctors found: " + doctors.size());
        for (Doctor doctor : doctors) {
            System.out.println(doctor.getDoctorId()
                    + " | " + doctor.getFirstName() + " " + doctor.getSurname()
                    + " | specialization " + valueOrUnknown(doctor.getSpecialization())
                    + " | hospital " + valueOrUnknown(doctor.getHospital()));
        }
    }

    private static void printDrugsByName(HospitalClaimsService service, String drugName) throws SQLException {
        List<Drug> drugs = service.findDrugsByName(drugName);
        if (drugs.isEmpty()) {
            System.out.println("No drugs found matching name: " + drugName);
            return;
        }

        System.out.println("Drugs found: " + drugs.size());
        for (Drug drug : drugs) {
            System.out.println(drug.getDrugId()
                    + " | " + drug.getDrugName()
                    + " | purpose " + valueOrUnknown(drug.getPurpose()));
        }
    }

    private static void printUsage() {
        System.out.println("Commands:");
        System.out.println("  menu");
        System.out.println("  create-demo-records");
        System.out.println("  create-insurance");
        System.out.println("  update-insurance");
        System.out.println("  create-patient");
        System.out.println("  create-doctor");
        System.out.println("  create-drug");
        System.out.println("  update-drug");
        System.out.println("  record-visit");
        System.out.println("  record-prescription");
        System.out.println("  update-patient");
        System.out.println("  update-doctor");
        System.out.println("  update-visit");
        System.out.println("  update-prescription");
        System.out.println("  delete-insurance");
        System.out.println("  delete-patient");
        System.out.println("  delete-doctor");
        System.out.println("  delete-drug");
        System.out.println("  delete-visit");
        System.out.println("  delete-prescription");
        System.out.println("  create-claim");
        System.out.println("  submit-claim");
        System.out.println("  review-claim");
        System.out.println("  approve-claim");
        System.out.println("  reject-claim");
        System.out.println("  delete-claim");
        System.out.println("  claim-queue [patientId]");
        System.out.println("  patient-history <patientId>");
        System.out.println("  claim-review [patientId]");
        System.out.println("  find-patients <surname-fragment>");
        System.out.println("  find-doctors <specialization-fragment>");
        System.out.println("  find-drugs <drug-name-fragment>");
        System.out.println("  serve [port]");
        System.out.println("  help");
    }

    /** Reads a required positional argument or raises a usage error. */
    private static String requireArgument(String[] args, int index, String usage) {
        if (args.length <= index || args[index].isBlank()) {
            throw new ValidationException("Expected arguments: " + usage);
        }
        return args[index].trim();
    }

    /** Reassembles a free-form tail of command arguments into one string. */
    private static String joinArgs(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            throw new ValidationException("Missing search text.");
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
    }

    private static String formatInsurance(Insurance insurance, String fallbackId) {
        if (insurance == null) {
            return valueOrUnknown(fallbackId);
        }
        return insurance.getInsuranceId() + " - " + valueOrUnknown(insurance.getCompany());
    }

    private static String formatDoctor(Doctor doctor, String fallbackId) {
        if (doctor == null) {
            return valueOrUnknown(fallbackId);
        }
        return doctor.getDoctorId() + " - " + doctor.getFirstName() + " " + doctor.getSurname();
    }

    private static String formatDrug(PrescriptionReview review) {
        if (review.drug() == null) {
            return valueOrUnknown(review.prescription().getDrugId());
        }
        return review.drug().getDrugId() + " - " + valueOrUnknown(review.drug().getDrugName());
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "n/a" : DATE_FORMAT.format(value);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /** Runs the menu-driven console interface. */
    private static void runInteractiveConsole(HospitalClaimsService service) throws SQLException {
        Scanner scanner = new Scanner(System.in);
        printSummary(service);

        while (true) {
            printMenu();
            String selection = prompt(scanner, "Select an option");

            try {
                switch (selection) {
                    case "1" -> printSummary(service);
                    case "2" -> createInsurance(service, scanner);
                    case "3" -> updateInsurance(service, scanner);
                    case "4" -> createPatient(service, scanner);
                    case "5" -> createDoctor(service, scanner);
                    case "6" -> createDrug(service, scanner);
                    case "7" -> updateDrug(service, scanner);
                    case "8" -> recordVisit(service, scanner);
                    case "9" -> recordPrescription(service, scanner);
                    case "10" -> updatePatient(service, scanner);
                    case "11" -> updateDoctor(service, scanner);
                    case "12" -> updateVisit(service, scanner);
                    case "13" -> updatePrescription(service, scanner);
                    case "14" -> createClaim(service, scanner);
                    case "15" -> submitClaim(service, scanner);
                    case "16" -> reviewClaim(service, scanner);
                    case "17" -> approveClaim(service, scanner);
                    case "18" -> rejectClaim(service, scanner);
                    case "19" -> printClaimsForPrompt(service, scanner);
                    case "20" -> deleteInsurance(service, scanner);
                    case "21" -> deletePatient(service, scanner);
                    case "22" -> deleteDoctor(service, scanner);
                    case "23" -> deleteDrug(service, scanner);
                    case "24" -> deleteVisit(service, scanner);
                    case "25" -> deletePrescription(service, scanner);
                    case "26" -> deleteClaim(service, scanner);
                    case "27" -> printPatientHistory(service, requireNonBlankPrompt(scanner, "Patient ID"));
                    case "28" -> printClaimReviewForPrompt(service, scanner);
                    case "29" -> printPatientsBySurname(service, requireNonBlankPrompt(scanner, "Surname fragment"));
                    case "30" -> printDoctorsBySpecialization(service, requireNonBlankPrompt(scanner, "Specialization fragment"));
                    case "31" -> printDrugsByName(service, requireNonBlankPrompt(scanner, "Drug name fragment"));
                    case "32" -> startHttpServer(service, scanner);
                    case "33", "q", "quit", "exit" -> {
                        System.out.println("Exiting.");
                        return;
                    }
                    default -> System.out.println("Unknown menu option: " + selection);
                }
            } catch (ValidationException exception) {
                System.out.println("Validation failed: " + exception.getMessage());
            } catch (SQLException exception) {
                System.out.println("Database operation failed: " + exception.getMessage());
            }
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("Claims Console Menu");
        System.out.println("  1. View database summary");
        System.out.println("  2. Register insurance provider");
        System.out.println("  3. Update insurance provider");
        System.out.println("  4. Register patient");
        System.out.println("  5. Register doctor");
        System.out.println("  6. Register drug");
        System.out.println("  7. Update drug");
        System.out.println("  8. Record visit");
        System.out.println("  9. Record prescription");
        System.out.println("  10. Update patient");
        System.out.println("  11. Update doctor");
        System.out.println("  12. Update visit");
        System.out.println("  13. Update prescription");
        System.out.println("  14. Create claim draft");
        System.out.println("  15. Submit claim");
        System.out.println("  16. Move claim to under review");
        System.out.println("  17. Approve claim");
        System.out.println("  18. Reject claim");
        System.out.println("  19. View claim queue");
        System.out.println("  20. Delete insurance provider");
        System.out.println("  21. Delete patient");
        System.out.println("  22. Delete doctor");
        System.out.println("  23. Delete drug");
        System.out.println("  24. Delete visit");
        System.out.println("  25. Delete prescription");
        System.out.println("  26. Delete claim");
        System.out.println("  27. View patient history");
        System.out.println("  28. View prescription claim review");
        System.out.println("  29. Search patients by surname");
        System.out.println("  30. Search doctors by specialization");
        System.out.println("  31. Search drugs by name");
        System.out.println("  32. Start browser interface");
        System.out.println("  33. Exit");
    }

    private static void createInsurance(HospitalClaimsService service) throws SQLException {
        createInsurance(service, new Scanner(System.in));
    }

    private static void createInsurance(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Insurance insurance = new Insurance(
                requireNonBlankPrompt(scanner, "Insurance ID"),
                requireNonBlankPrompt(scanner, "Company"),
                optionalPrompt(scanner, "Address"),
                optionalPrompt(scanner, "Phone")
        );
        service.registerInsurance(insurance);
        System.out.println("Insurance provider saved: " + insurance.getInsuranceId());
    }

    private static void updateInsurance(HospitalClaimsService service) throws SQLException {
        updateInsurance(service, new Scanner(System.in));
    }

    private static void updateInsurance(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Insurance existing = service.findInsuranceById(requireNonBlankPrompt(scanner, "Existing insurance ID"))
                .orElseThrow(() -> new ValidationException("Insurance provider was not found."));
        explainEditInputRules();

        Insurance insurance = new Insurance(
                existing.getInsuranceId(),
                promptRequiredWithDefault(scanner, "Company", existing.getCompany()),
                promptOptionalWithDefault(scanner, "Address", existing.getAddress()),
                promptOptionalWithDefault(scanner, "Phone", existing.getPhone())
        );
        service.updateInsurance(insurance);
        System.out.println("Insurance provider updated: " + insurance.getInsuranceId());
    }

    private static void createPatient(HospitalClaimsService service) throws SQLException {
        createPatient(service, new Scanner(System.in));
    }

    private static void createPatient(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Patient patient = new Patient(
                requireNonBlankPrompt(scanner, "Patient ID"),
                requireNonBlankPrompt(scanner, "First name"),
                requireNonBlankPrompt(scanner, "Surname"),
                optionalPrompt(scanner, "Postcode"),
                optionalPrompt(scanner, "Address"),
                optionalPrompt(scanner, "Phone"),
                requireNonBlankPrompt(scanner, "Email"),
                optionalPrompt(scanner, "Insurance ID (optional)"),
                optionalPrompt(scanner, "Primary care doctor ID (optional)")
        );
        service.registerPatient(patient);
        System.out.println("Patient saved: " + patient.getPatientId());
    }

    private static void createDoctor(HospitalClaimsService service) throws SQLException {
        createDoctor(service, new Scanner(System.in));
    }

    private static void createDoctor(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Doctor doctor = new Doctor(
                requireNonBlankPrompt(scanner, "Doctor ID"),
                requireNonBlankPrompt(scanner, "First name"),
                requireNonBlankPrompt(scanner, "Surname"),
                optionalPrompt(scanner, "Address"),
                optionalPrompt(scanner, "Phone"),
                requireNonBlankPrompt(scanner, "Email"),
                optionalPrompt(scanner, "Specialization"),
                optionalPrompt(scanner, "Hospital or clinic")
        );
        service.registerDoctor(doctor);
        System.out.println("Doctor saved: " + doctor.getDoctorId());
    }

    private static void createDrug(HospitalClaimsService service) throws SQLException {
        createDrug(service, new Scanner(System.in));
    }

    private static void createDrug(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Drug drug = new Drug(
                requireNonBlankPrompt(scanner, "Drug ID"),
                requireNonBlankPrompt(scanner, "Drug name"),
                optionalPrompt(scanner, "Possible side effects"),
                optionalPrompt(scanner, "Purpose")
        );
        service.registerDrug(drug);
        System.out.println("Drug saved: " + drug.getDrugId());
    }

    private static void updateDrug(HospitalClaimsService service) throws SQLException {
        updateDrug(service, new Scanner(System.in));
    }

    private static void updateDrug(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Drug existing = service.findDrugById(requireNonBlankPrompt(scanner, "Existing drug ID"))
                .orElseThrow(() -> new ValidationException("Drug was not found."));
        explainEditInputRules();

        Drug drug = new Drug(
                existing.getDrugId(),
                promptRequiredWithDefault(scanner, "Drug name", existing.getDrugName()),
                promptOptionalWithDefault(scanner, "Possible side effects", existing.getSideEffects()),
                promptOptionalWithDefault(scanner, "Purpose", existing.getPurpose())
        );
        service.updateDrug(drug);
        System.out.println("Drug updated: " + drug.getDrugId());
    }

    private static void recordVisit(HospitalClaimsService service) throws SQLException {
        recordVisit(service, new Scanner(System.in));
    }

    private static void recordVisit(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Visit visit = new Visit(
                requireNonBlankPrompt(scanner, "Patient ID"),
                requireNonBlankPrompt(scanner, "Doctor ID"),
                promptDate(scanner, "Visit date (YYYY-MM-DD)"),
                optionalPrompt(scanner, "Symptoms"),
                optionalPrompt(scanner, "Diagnosis code")
        );
        service.recordVisit(visit);
        System.out.println("Visit saved for patient " + visit.getPatientId() + " on " + DATE_FORMAT.format(visit.getDateOfVisit()));
    }

    private static void recordPrescription(HospitalClaimsService service) throws SQLException {
        recordPrescription(service, new Scanner(System.in));
    }

    private static void recordPrescription(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Prescription prescription = new Prescription(
                requireNonBlankPrompt(scanner, "Prescription ID"),
                promptDate(scanner, "Prescription date (YYYY-MM-DD)"),
                requireNonBlankPrompt(scanner, "Dosage"),
                requireNonBlankPrompt(scanner, "Duration in days"),
                optionalPrompt(scanner, "Comment"),
                requireNonBlankPrompt(scanner, "Drug ID"),
                requireNonBlankPrompt(scanner, "Doctor ID"),
                requireNonBlankPrompt(scanner, "Patient ID")
        );
        service.recordPrescription(prescription);
        System.out.println("Prescription saved: " + prescription.getPrescriptionId());
    }

    private static void updatePatient(HospitalClaimsService service) throws SQLException {
        updatePatient(service, new Scanner(System.in));
    }

    private static void updatePatient(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Patient existing = service.findPatientById(requireNonBlankPrompt(scanner, "Existing patient ID"))
                .orElseThrow(() -> new ValidationException("Patient was not found."));
        explainEditInputRules();

        Patient patient = new Patient(
                existing.getPatientId(),
                promptRequiredWithDefault(scanner, "First name", existing.getFirstName()),
                promptRequiredWithDefault(scanner, "Surname", existing.getSurname()),
                promptOptionalWithDefault(scanner, "Postcode", existing.getPostcode()),
                promptOptionalWithDefault(scanner, "Address", existing.getAddress()),
                promptOptionalWithDefault(scanner, "Phone", existing.getPhone()),
                promptRequiredWithDefault(scanner, "Email", existing.getEmail()),
                promptOptionalWithDefault(scanner, "Insurance ID", existing.getInsuranceId()),
                promptOptionalWithDefault(scanner, "Primary care doctor ID", existing.getPrimaryCareDoctorId())
        );
        service.updatePatient(patient);
        System.out.println("Patient updated: " + patient.getPatientId());
    }

    private static void updateDoctor(HospitalClaimsService service) throws SQLException {
        updateDoctor(service, new Scanner(System.in));
    }

    private static void updateDoctor(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Doctor existing = service.findDoctorById(requireNonBlankPrompt(scanner, "Existing doctor ID"))
                .orElseThrow(() -> new ValidationException("Doctor was not found."));
        explainEditInputRules();

        Doctor doctor = new Doctor(
                existing.getDoctorId(),
                promptRequiredWithDefault(scanner, "First name", existing.getFirstName()),
                promptRequiredWithDefault(scanner, "Surname", existing.getSurname()),
                promptOptionalWithDefault(scanner, "Address", existing.getAddress()),
                promptOptionalWithDefault(scanner, "Phone", existing.getPhone()),
                promptRequiredWithDefault(scanner, "Email", existing.getEmail()),
                promptOptionalWithDefault(scanner, "Specialization", existing.getSpecialization()),
                promptOptionalWithDefault(scanner, "Hospital or clinic", existing.getHospital())
        );
        service.updateDoctor(doctor);
        System.out.println("Doctor updated: " + doctor.getDoctorId());
    }

    private static void updateVisit(HospitalClaimsService service) throws SQLException {
        updateVisit(service, new Scanner(System.in));
    }

    private static void updateVisit(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String patientId = requireNonBlankPrompt(scanner, "Existing visit patient ID");
        String doctorId = requireNonBlankPrompt(scanner, "Existing visit doctor ID");
        LocalDate dateOfVisit = promptDate(scanner, "Existing visit date (YYYY-MM-DD)");
        Visit existing = service.findVisit(patientId, doctorId, dateOfVisit)
                .orElseThrow(() -> new ValidationException("Visit was not found."));
        explainEditInputRules();

        Visit visit = new Visit(
                promptRequiredWithDefault(scanner, "Patient ID", existing.getPatientId()),
                promptRequiredWithDefault(scanner, "Doctor ID", existing.getDoctorId()),
                promptDateWithDefault(scanner, "Visit date (YYYY-MM-DD)", existing.getDateOfVisit()),
                promptOptionalWithDefault(scanner, "Symptoms", existing.getSymptoms()),
                promptOptionalWithDefault(scanner, "Diagnosis code", existing.getDiagnosisId())
        );
        service.updateVisit(patientId, doctorId, dateOfVisit, visit);
        System.out.println("Visit updated for patient " + visit.getPatientId() + " on " + DATE_FORMAT.format(visit.getDateOfVisit()));
    }

    private static void updatePrescription(HospitalClaimsService service) throws SQLException {
        updatePrescription(service, new Scanner(System.in));
    }

    private static void updatePrescription(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Prescription existing = service.findPrescriptionById(requireNonBlankPrompt(scanner, "Existing prescription ID"))
                .orElseThrow(() -> new ValidationException("Prescription was not found."));
        explainEditInputRules();

        Prescription prescription = new Prescription(
                existing.getPrescriptionId(),
                promptDateWithDefault(scanner, "Prescription date (YYYY-MM-DD)", existing.getDatePrescribed()),
                promptRequiredWithDefault(scanner, "Dosage", existing.getDosage()),
                promptRequiredWithDefault(scanner, "Duration in days", existing.getDuration()),
                promptOptionalWithDefault(scanner, "Comment", existing.getComment()),
                promptRequiredWithDefault(scanner, "Drug ID", existing.getDrugId()),
                promptRequiredWithDefault(scanner, "Doctor ID", existing.getDoctorId()),
                promptRequiredWithDefault(scanner, "Patient ID", existing.getPatientId())
        );
        service.updatePrescription(prescription);
        System.out.println("Prescription updated: " + prescription.getPrescriptionId());
    }

    private static void createClaim(HospitalClaimsService service) throws SQLException {
        createClaim(service, new Scanner(System.in));
    }

    private static void createClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Claim claim = service.createClaim(
                requireNonBlankPrompt(scanner, "Claim ID"),
                requireNonBlankPrompt(scanner, "Prescription ID"),
                optionalPrompt(scanner, "Initial notes (optional)")
        );
        System.out.println("Claim created: " + claim.getClaimId() + " | status " + claim.getStatus());
    }

    private static void submitClaim(HospitalClaimsService service) throws SQLException {
        submitClaim(service, new Scanner(System.in));
    }

    private static void submitClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Claim claim = service.submitClaim(requireNonBlankPrompt(scanner, "Claim ID"));
        System.out.println("Claim submitted: " + claim.getClaimId());
    }

    private static void reviewClaim(HospitalClaimsService service) throws SQLException {
        reviewClaim(service, new Scanner(System.in));
    }

    private static void reviewClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Claim claim = service.markClaimUnderReview(
                requireNonBlankPrompt(scanner, "Claim ID"),
                requireNonBlankPrompt(scanner, "Reviewer")
        );
        System.out.println("Claim moved to under review: " + claim.getClaimId());
    }

    private static void approveClaim(HospitalClaimsService service) throws SQLException {
        approveClaim(service, new Scanner(System.in));
    }

    private static void approveClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Claim claim = service.approveClaim(
                requireNonBlankPrompt(scanner, "Claim ID"),
                requireNonBlankPrompt(scanner, "Reviewer"),
                optionalPrompt(scanner, "Decision notes (optional)")
        );
        System.out.println("Claim approved: " + claim.getClaimId());
    }

    private static void rejectClaim(HospitalClaimsService service) throws SQLException {
        rejectClaim(service, new Scanner(System.in));
    }

    private static void rejectClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        Claim claim = service.rejectClaim(
                requireNonBlankPrompt(scanner, "Claim ID"),
                requireNonBlankPrompt(scanner, "Reviewer"),
                requireNonBlankPrompt(scanner, "Decision notes")
        );
        System.out.println("Claim rejected: " + claim.getClaimId());
    }

    private static void deleteInsurance(HospitalClaimsService service) throws SQLException {
        deleteInsurance(service, new Scanner(System.in));
    }

    private static void deleteInsurance(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String insuranceId = requireNonBlankPrompt(scanner, "Insurance ID to delete");
        confirmDeletion(scanner, "insurance " + insuranceId);
        service.deleteInsurance(insuranceId);
        System.out.println("Insurance provider deleted: " + insuranceId);
    }

    private static void deletePatient(HospitalClaimsService service) throws SQLException {
        deletePatient(service, new Scanner(System.in));
    }

    private static void deletePatient(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String patientId = requireNonBlankPrompt(scanner, "Patient ID to delete");
        confirmDeletion(scanner, "patient " + patientId);
        service.deletePatient(patientId);
        System.out.println("Patient deleted: " + patientId);
    }

    private static void deleteDoctor(HospitalClaimsService service) throws SQLException {
        deleteDoctor(service, new Scanner(System.in));
    }

    private static void deleteDoctor(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String doctorId = requireNonBlankPrompt(scanner, "Doctor ID to delete");
        confirmDeletion(scanner, "doctor " + doctorId);
        service.deleteDoctor(doctorId);
        System.out.println("Doctor deleted: " + doctorId);
    }

    private static void deleteDrug(HospitalClaimsService service) throws SQLException {
        deleteDrug(service, new Scanner(System.in));
    }

    private static void deleteDrug(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String drugId = requireNonBlankPrompt(scanner, "Drug ID to delete");
        confirmDeletion(scanner, "drug " + drugId);
        service.deleteDrug(drugId);
        System.out.println("Drug deleted: " + drugId);
    }

    private static void deleteVisit(HospitalClaimsService service) throws SQLException {
        deleteVisit(service, new Scanner(System.in));
    }

    private static void deleteVisit(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String patientId = requireNonBlankPrompt(scanner, "Visit patient ID to delete");
        String doctorId = requireNonBlankPrompt(scanner, "Visit doctor ID to delete");
        LocalDate date = promptDate(scanner, "Visit date to delete (YYYY-MM-DD)");
        confirmDeletion(scanner, "visit " + patientId + "/" + doctorId + "/" + date);
        service.deleteVisit(patientId, doctorId, date);
        System.out.println("Visit deleted: " + patientId + "/" + doctorId + "/" + date);
    }

    private static void deletePrescription(HospitalClaimsService service) throws SQLException {
        deletePrescription(service, new Scanner(System.in));
    }

    private static void deletePrescription(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String prescriptionId = requireNonBlankPrompt(scanner, "Prescription ID to delete");
        confirmDeletion(scanner, "prescription " + prescriptionId);
        service.deletePrescription(prescriptionId);
        System.out.println("Prescription deleted: " + prescriptionId);
    }

    private static void deleteClaim(HospitalClaimsService service) throws SQLException {
        deleteClaim(service, new Scanner(System.in));
    }

    private static void deleteClaim(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String claimId = requireNonBlankPrompt(scanner, "Claim ID to delete");
        confirmDeletion(scanner, "claim " + claimId);
        service.deleteClaim(claimId);
        System.out.println("Claim deleted: " + claimId);
    }

    private static void printClaimReviewForPrompt(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String patientId = optionalPrompt(scanner, "Patient ID filter (optional)");
        if (patientId == null) {
            printClaimReview(service, new String[]{"claim-review"});
            return;
        }
        printClaimReview(service, new String[]{"claim-review", patientId});
    }

    private static void printClaimsForPrompt(HospitalClaimsService service, Scanner scanner) throws SQLException {
        String patientId = optionalPrompt(scanner, "Patient ID filter (optional)");
        if (patientId == null) {
            printClaims(service, new String[]{"claim-queue"});
            return;
        }
        printClaims(service, new String[]{"claim-queue", patientId});
    }

    private static String requireNonBlankPrompt(Scanner scanner, String label) {
        String value = prompt(scanner, label);
        if (value.isBlank()) {
            throw new ValidationException(label + " must not be blank.");
        }
        return value.trim();
    }

    private static String optionalPrompt(Scanner scanner, String label) {
        String value = prompt(scanner, label);
        return value.isBlank() ? null : value.trim();
    }

    private static String prompt(Scanner scanner, String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }

    private static void explainEditInputRules() {
        System.out.println("Leave a field blank to keep the current value. Enter - to clear optional fields.");
    }

    private static void confirmDeletion(Scanner scanner, String label) {
        String confirmation = requireNonBlankPrompt(scanner, "Type DELETE to confirm removal of " + label);
        if (!"DELETE".equals(confirmation)) {
            throw new ValidationException("Deletion cancelled because confirmation text did not match DELETE.");
        }
    }

    private static String promptRequiredWithDefault(Scanner scanner, String label, String currentValue) {
        String value = prompt(scanner, label + " [" + currentValue + "]");
        return value.isBlank() ? currentValue : value.trim();
    }

    private static String promptOptionalWithDefault(Scanner scanner, String label, String currentValue) {
        String currentDisplay = currentValue == null || currentValue.isBlank() ? "empty" : currentValue;
        String value = prompt(scanner, label + " [" + currentDisplay + "]");
        if (value.isBlank()) {
            return currentValue;
        }
        String trimmed = value.trim();
        return "-".equals(trimmed) ? null : trimmed;
    }

    private static LocalDate promptDate(Scanner scanner, String label) {
        String value = requireNonBlankPrompt(scanner, label);
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (Exception exception) {
            throw new ValidationException(label + " must use YYYY-MM-DD.");
        }
    }

    private static LocalDate promptDateWithDefault(Scanner scanner, String label, LocalDate currentValue) {
        String value = prompt(scanner, label + " [" + DATE_FORMAT.format(currentValue) + "]");
        if (value.isBlank()) {
            return currentValue;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (Exception exception) {
            throw new ValidationException(label + " must use YYYY-MM-DD.");
        }
    }

    private static String formatIssues(List<String> issues) {
        return issues.isEmpty() ? "none" : String.join("; ", issues);
    }

    /** Starts the HTTP server using the CLI argument when one is present. */
    private static void startHttpServer(HospitalClaimsService service, String[] args) {
        int port = 8080;
        if (args.length > 1 && !args[1].isBlank()) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException exception) {
                throw new ValidationException("Port must be a number.");
            }
        }
        startHttpServer(service, port);
    }

    /** Starts the HTTP server after prompting for a port in interactive mode. */
    private static void startHttpServer(HospitalClaimsService service, Scanner scanner) {
        String portInput = optionalPrompt(scanner, "HTTP port (optional, default 8080)");
        int port = 8080;
        if (portInput != null) {
            try {
                port = Integer.parseInt(portInput);
            } catch (NumberFormatException exception) {
                throw new ValidationException("Port must be a number.");
            }
        }
        startHttpServer(service, port);
    }

    /** Boots the browser-facing operations server on the requested port. */
    private static void startHttpServer(HospitalClaimsService service, int port) {
        try {
            HospitalClaimsHttpServer server = new HospitalClaimsHttpServer(service, port);
            server.start();
            System.out.println("HTTP interface started at http://localhost:" + server.getPort());
            System.out.println("Press Ctrl+C to stop the server.");
            Thread.currentThread().join();
        } catch (ValidationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ValidationException("HTTP server interrupted.");
        } catch (Exception exception) {
            throw new ValidationException("Unable to start HTTP interface: " + exception.getMessage());
        }
    }

    private static void createDemoRecords(HospitalClaimsService service) throws SQLException {
        Optional<Doctor> doctor = service.getAllDoctors().stream().findFirst();
        Optional<Drug> drug = service.getAllDrugs().stream().findFirst();

        if (doctor.isEmpty() || drug.isEmpty()) {
            System.out.println("Skipped demo inserts because doctor or drug seed data is missing.");
            return;
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String insuranceId = "DMO" + suffix.substring(0, 5);
        String patientId = "PT" + suffix.substring(0, 6);
        String prescriptionId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        Insurance insurance = new Insurance(insuranceId, "Demo Health " + suffix, "1 Example Way", "000-000-0000");
        Patient patient = new Patient(
                patientId,
                "Demo",
                "Patient",
                "AB1 2CD",
                "1 Example Way",
                "07000000000",
                "demo.patient." + suffix.toLowerCase(Locale.ROOT) + "@example.com",
                insuranceId,
                doctor.get().getDoctorId()
        );
        Visit visit = new Visit(patientId, doctor.get().getDoctorId(), LocalDate.now(), "Demonstration symptoms", "DEMO-1");
        Prescription prescription = new Prescription(
                prescriptionId,
                LocalDate.now(),
                "1",
                "7",
                "Created by application demo workflow",
                drug.get().getDrugId(),
                doctor.get().getDoctorId(),
                patientId
        );

        service.registerInsurance(insurance);
        service.registerPatient(patient);
        service.recordVisit(visit);
        service.recordPrescription(prescription);

        System.out.println("Demo records created:");
        System.out.println("Insurance ID: " + insuranceId);
        System.out.println("Patient ID: " + patientId);
        System.out.println("Visit key: " + patientId + " / " + doctor.get().getDoctorId() + " / " + LocalDate.now());
        System.out.println("Prescription ID: " + prescriptionId);
    }
}
