package com.hospitalclaims.service;

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
import com.hospitalclaims.repository.VisitKey;
import com.hospitalclaims.repository.VisitRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalClaimsServiceTest {
    private static final DatabaseConnection UNUSED_CONNECTION = new DatabaseConnection();

    @Test
    void getPatientHistoryReportIncludesLinkedCoverageAndClinicalData() throws SQLException {
        Insurance insurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", "DOC1");
        Doctor primaryDoctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Doctor prescribingDoctor = new Doctor("DOC2", "Noah", "Wells", "4 Main St", "02070000002",
                "noah.wells@example.com", "General Practice", "Community Clinic");
        Drug drug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Visit visit = new Visit("PAT1", "DOC2", LocalDate.of(2026, 5, 1), "Chest pain", "DX-1");
        Prescription prescription = new Prescription("RX1", LocalDate.now().minusDays(1), "1 tablet", "5",
                "Take after food", "DRUG1", "DOC2", "PAT1");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of(insurance)),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(primaryDoctor, prescribingDoctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(prescription)),
                new FakeVisitRepository(List.of(visit))
        );

        PatientHistoryReport report = service.getPatientHistoryReport("PAT1").orElseThrow();

        assertEquals("PAT1", report.patient().getPatientId());
        assertEquals("Northwind Health", report.insurance().getCompany());
        assertEquals("DOC1", report.primaryCareDoctor().getDoctorId());
        assertEquals(1, report.visits().size());
        assertEquals(1, report.prescriptions().size());
        assertEquals(LocalDate.now().plusDays(3), report.prescriptions().getFirst().endDate());
        assertTrue(report.prescriptions().getFirst().active());
        assertTrue(report.prescriptions().getFirst().eligible());
        assertEquals("DOC2", report.prescriptions().getFirst().doctor().getDoctorId());
        assertEquals("DRUG1", report.prescriptions().getFirst().drug().getDrugId());
    }

    @Test
    void recordPrescriptionRejectsOverlappingActivePrescriptionForSamePatientAndDrug() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", null, null);
        Doctor firstDoctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Doctor secondDoctor = new Doctor("DOC2", "Noah", "Wells", "4 Main St", "02070000002",
                "noah.wells@example.com", "General Practice", "Community Clinic");
        Drug drug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Prescription existing = new Prescription("RX1", LocalDate.of(2026, 5, 1), "1 tablet", "10",
                null, "DRUG1", "DOC1", "PAT1");
        Prescription incoming = new Prescription("RX2", LocalDate.of(2026, 5, 5), "1 tablet", "7",
                null, "DRUG1", "DOC2", "PAT1");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(firstDoctor, secondDoctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(existing)),
                new FakeVisitRepository(List.of())
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.recordPrescription(incoming)
        );

        assertEquals("patient already has an active prescription for drugId DRUG1.", exception.getMessage());
    }

    @Test
    void registerPatientRejectsUnknownInsuranceReference() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS404", null);

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of()),
                new FakeDoctorRepository(List.of()),
                new FakeDrugRepository(List.of()),
                new FakePrescriptionRepository(List.of()),
                new FakeVisitRepository(List.of())
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.registerPatient(patient)
        );

        assertEquals("insuranceId INS404 does not exist.", exception.getMessage());
    }

    @Test
    void recordVisitRejectsUnknownDoctorReference() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", null, null);
        Visit visit = new Visit("PAT1", "DOC404", LocalDate.of(2026, 5, 7), "Fever", "DX-9");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of()),
                new FakeDrugRepository(List.of()),
                new FakePrescriptionRepository(List.of()),
                new FakeVisitRepository(List.of())
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.recordVisit(visit)
        );

        assertEquals("doctorId DOC404 does not exist.", exception.getMessage());
    }

    @Test
    void recordPrescriptionRejectsUnknownDrugReference() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", null, null);
        Doctor doctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Prescription prescription = new Prescription("RX1", LocalDate.of(2026, 5, 8), "1 tablet", "7",
                null, "DRUG404", "DOC1", "PAT1");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of()),
                new FakePrescriptionRepository(List.of()),
                new FakeVisitRepository(List.of())
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.recordPrescription(prescription)
        );

        assertEquals("drugId DRUG404 does not exist.", exception.getMessage());
    }

    @Test
    void getPrescriptionReviewsMarksExpiredPrescriptionInactive() throws SQLException {
        Patient patient = new Patient("PAT9", "Kai", "Price", "AB1 2CD", "2 Main St", "07111111111",
                "kai.price@example.com", null, null);
        Prescription expired = new Prescription("RX9", LocalDate.now().minusDays(10), "10ml", "3",
                null, "DRUG9", "DOC9", "PAT9");
        Drug drug = new Drug("DRUG9", "Ibuprofen", null, "Pain relief");
        Doctor doctor = new Doctor("DOC9", "Ivy", "Hart", null, null, "ivy.hart@example.com", "Pain", "City Hospital");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(expired)),
                new FakeVisitRepository(List.of())
        );

        PrescriptionReview review = service.getPrescriptionReviews().getFirst();

        assertEquals(LocalDate.now().minusDays(8), review.endDate());
        assertFalse(review.active());
        assertFalse(review.eligible());
        assertTrue(review.eligibilityIssues().contains("patient has no insurance coverage on file."));
    }

    @Test
    void getPrescriptionReviewsFlagsMissingVisitAndLongDurationWithoutComment() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", null);
        Doctor doctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Drug drug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Prescription prescription = new Prescription("RX1", LocalDate.of(2026, 5, 8), "1 tablet", "45",
                null, "DRUG1", "DOC1", "PAT1");

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of(new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000"))),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(prescription)),
                new FakeVisitRepository(List.of())
        );

        PrescriptionReview review = service.getPrescriptionReviews().getFirst();

        assertFalse(review.eligible());
        assertTrue(review.eligibilityIssues().contains(
                "no matching visit with the prescribing doctor exists within 30 days before the prescription date."
        ));
        assertTrue(review.eligibilityIssues().contains(
                "prescriptions longer than 30 days require a clinical comment for claim review."
        ));
    }

    @Test
    void updatePatientAllowsClearingOptionalReferences() throws SQLException {
        Insurance insurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Doctor doctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Patient original = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", "DOC1");
        Patient updated = new Patient("PAT1", "Ava", "Coleman", "AB1 2CD", "4 Main St", null,
                "ava.coleman@example.com", null, null);

        FakePatientRepository patientRepository = new FakePatientRepository(List.of(original));
        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of(insurance)),
                patientRepository,
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of()),
                new FakePrescriptionRepository(List.of()),
                new FakeVisitRepository(List.of())
        );

        service.updatePatient(updated);

        Patient stored = patientRepository.findById("PAT1").orElseThrow();
        assertEquals("Coleman", stored.getSurname());
        assertEquals("4 Main St", stored.getAddress());
        assertNull(stored.getPhone());
        assertNull(stored.getInsuranceId());
        assertNull(stored.getPrimaryCareDoctorId());
    }

    @Test
    void updateVisitReplacesStoredVisitUsingOriginalCompositeKey() throws SQLException {
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", null, null);
        Doctor oldDoctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Doctor newDoctor = new Doctor("DOC2", "Noah", "Wells", "4 Main St", "02070000002",
                "noah.wells@example.com", "General Practice", "Community Clinic");
        Visit original = new Visit("PAT1", "DOC1", LocalDate.of(2026, 5, 7), "Fever", "DX-9");
        Visit updated = new Visit("PAT1", "DOC2", LocalDate.of(2026, 5, 8), "Recovered", "DX-10");

        FakeVisitRepository visitRepository = new FakeVisitRepository(List.of(original));
        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                new FakeInsuranceRepository(List.of()),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(oldDoctor, newDoctor)),
                new FakeDrugRepository(List.of()),
                new FakePrescriptionRepository(List.of()),
                visitRepository
        );

        service.updateVisit("PAT1", "DOC1", LocalDate.of(2026, 5, 7), updated);

        Visit stored = visitRepository.findById(new VisitKey("PAT1", "DOC2", LocalDate.of(2026, 5, 8))).orElseThrow();
        assertEquals("Recovered", stored.getSymptoms());
        assertTrue(visitRepository.findById(new VisitKey("PAT1", "DOC1", LocalDate.of(2026, 5, 7))).isEmpty());
    }

    @Test
    void createAndApproveClaimTracksSeparateClaimLifecycle() throws SQLException {
        Insurance insurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", null);
        Doctor doctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Drug drug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Prescription prescription = new Prescription("RX1", LocalDate.now().minusDays(1), "1 tablet", "5",
                "Take after food", "DRUG1", "DOC1", "PAT1");
        Visit visit = new Visit("PAT1", "DOC1", LocalDate.now().minusDays(2), "Chest pain", "DX-1");
        FakeClaimRepository claimRepository = new FakeClaimRepository(List.of());

        HospitalClaimsService service = new HospitalClaimsService(
                claimRepository,
                new FakeInsuranceRepository(List.of(insurance)),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(prescription)),
                new FakeVisitRepository(List.of(visit))
        );

        service.createClaim("CLM1", "RX1", "Created for review");
        service.submitClaim("CLM1");
        Claim submitted = claimRepository.findById("CLM1").orElseThrow();
        assertEquals(ClaimStatus.SUBMITTED.name(), submitted.getStatus());
        Claim approved = service.approveClaim("CLM1", "reviewer@example.com", "Approved after validation");
        Claim stored = claimRepository.findById("CLM1").orElseThrow();

        assertEquals("PAT1", stored.getPatientId());
        assertEquals("INS1", stored.getInsuranceId());
        assertEquals(ClaimStatus.APPROVED.name(), approved.getStatus());
        assertEquals("reviewer@example.com", approved.getReviewedBy());
        assertEquals(LocalDate.now(), approved.getDecisionDate());
        assertEquals(1, service.getClaimViews().size());
        assertEquals(ClaimStatus.APPROVED.name(), stored.getStatus());
    }

    @Test
    void deletePrescriptionRejectsWhenClaimExists() throws SQLException {
        Insurance insurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Patient patient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", null);
        Doctor doctor = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Drug drug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Prescription prescription = new Prescription("RX1", LocalDate.now().minusDays(1), "1 tablet", "5",
                "Take after food", "DRUG1", "DOC1", "PAT1");
        Claim claim = new Claim("CLM1", "RX1", "PAT1", "INS1", ClaimStatus.DRAFT.name(),
                LocalDate.now(), null, null, null, null);

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of(claim)),
                new FakeInsuranceRepository(List.of(insurance)),
                new FakePatientRepository(List.of(patient)),
                new FakeDoctorRepository(List.of(doctor)),
                new FakeDrugRepository(List.of(drug)),
                new FakePrescriptionRepository(List.of(prescription)),
                new FakeVisitRepository(List.of())
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.deletePrescription("RX1")
        );

        assertEquals("prescriptionId RX1 still has a claim on file.", exception.getMessage());
    }

    @Test
    void updateInsuranceAndDrugPersistChanges() throws SQLException {
        Insurance originalInsurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Drug originalDrug = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        FakeInsuranceRepository insuranceRepository = new FakeInsuranceRepository(List.of(originalInsurance));
        FakeDrugRepository drugRepository = new FakeDrugRepository(List.of(originalDrug));

        HospitalClaimsService service = new HospitalClaimsService(
                new FakeClaimRepository(List.of()),
                insuranceRepository,
                new FakePatientRepository(List.of()),
                new FakeDoctorRepository(List.of()),
                drugRepository,
                new FakePrescriptionRepository(List.of()),
                new FakeVisitRepository(List.of())
        );

        service.updateInsurance(new Insurance("INS1", "Northwind Plus", null, "02079990000"));
        service.updateDrug(new Drug("DRUG1", "Amoxicillin XR", null, "Extended antibiotic"));

        assertEquals("Northwind Plus", insuranceRepository.findById("INS1").orElseThrow().getCompany());
        assertEquals("Amoxicillin XR", drugRepository.findById("DRUG1").orElseThrow().getDrugName());
        assertNull(insuranceRepository.findById("INS1").orElseThrow().getAddress());
        assertNull(drugRepository.findById("DRUG1").orElseThrow().getSideEffects());
    }

    private static final class FakeInsuranceRepository extends InsuranceRepository {
        private final List<Insurance> insurances;

        private FakeInsuranceRepository(List<Insurance> insurances) {
            super(UNUSED_CONNECTION);
            this.insurances = new ArrayList<>(insurances);
        }

        @Override
        public Optional<Insurance> findById(String insuranceId) {
            return insurances.stream().filter(item -> item.getInsuranceId().equals(insuranceId)).findFirst();
        }

        @Override
        public List<Insurance> findAll() {
            return List.copyOf(insurances);
        }

        @Override
        public void update(Insurance insurance) {
            insurances.removeIf(item -> item.getInsuranceId().equals(insurance.getInsuranceId()));
            insurances.add(insurance);
        }

        @Override
        public void deleteById(String insuranceId) {
            insurances.removeIf(item -> item.getInsuranceId().equals(insuranceId));
        }
    }

    private static final class FakePatientRepository extends PatientRepository {
        private final List<Patient> patients;

        private FakePatientRepository(List<Patient> patients) {
            super(UNUSED_CONNECTION);
            this.patients = new ArrayList<>(patients);
        }

        @Override
        public Optional<Patient> findById(String patientId) {
            return patients.stream().filter(item -> item.getPatientId().equals(patientId)).findFirst();
        }

        @Override
        public List<Patient> findBySurname(String surname) {
            String token = surname.toLowerCase();
            return patients.stream()
                    .filter(item -> item.getSurname().toLowerCase().contains(token))
                    .toList();
        }

        @Override
        public List<Patient> findAll() {
            return List.copyOf(patients);
        }

        @Override
        public void update(Patient patient) {
            patients.removeIf(item -> item.getPatientId().equals(patient.getPatientId()));
            patients.add(patient);
        }
    }

    private static final class FakeDoctorRepository extends DoctorRepository {
        private final List<Doctor> doctors;

        private FakeDoctorRepository(List<Doctor> doctors) {
            super(UNUSED_CONNECTION);
            this.doctors = new ArrayList<>(doctors);
        }

        @Override
        public Optional<Doctor> findById(String doctorId) {
            return doctors.stream().filter(item -> item.getDoctorId().equals(doctorId)).findFirst();
        }

        @Override
        public List<Doctor> findBySpecialization(String specialization) {
            String token = specialization.toLowerCase();
            return doctors.stream()
                    .filter(item -> item.getSpecialization() != null
                            && item.getSpecialization().toLowerCase().contains(token))
                    .toList();
        }

        @Override
        public List<Doctor> findAll() {
            return List.copyOf(doctors);
        }

        @Override
        public void update(Doctor doctor) {
            doctors.removeIf(item -> item.getDoctorId().equals(doctor.getDoctorId()));
            doctors.add(doctor);
        }

        @Override
        public void deleteById(String doctorId) {
            doctors.removeIf(item -> item.getDoctorId().equals(doctorId));
        }
    }

    private static final class FakeDrugRepository extends DrugRepository {
        private final List<Drug> drugs;

        private FakeDrugRepository(List<Drug> drugs) {
            super(UNUSED_CONNECTION);
            this.drugs = new ArrayList<>(drugs);
        }

        @Override
        public Optional<Drug> findById(String drugId) {
            return drugs.stream().filter(item -> item.getDrugId().equals(drugId)).findFirst();
        }

        @Override
        public List<Drug> findByName(String drugName) {
            String token = drugName.toLowerCase();
            return drugs.stream()
                    .filter(item -> item.getDrugName().toLowerCase().contains(token))
                    .toList();
        }

        @Override
        public List<Drug> findAll() {
            return List.copyOf(drugs);
        }

        @Override
        public void update(Drug drug) {
            drugs.removeIf(item -> item.getDrugId().equals(drug.getDrugId()));
            drugs.add(drug);
        }

        @Override
        public void deleteById(String drugId) {
            drugs.removeIf(item -> item.getDrugId().equals(drugId));
        }
    }

    private static final class FakePrescriptionRepository extends PrescriptionRepository {
        private final List<Prescription> prescriptions;

        private FakePrescriptionRepository(List<Prescription> prescriptions) {
            super(UNUSED_CONNECTION);
            this.prescriptions = new ArrayList<>(prescriptions);
        }

        @Override
        public void save(Prescription prescription) {
            prescriptions.add(prescription);
        }

        @Override
        public Optional<Prescription> findById(String prescriptionId) {
            return prescriptions.stream().filter(item -> item.getPrescriptionId().equals(prescriptionId)).findFirst();
        }

        @Override
        public void update(Prescription prescription) {
            prescriptions.removeIf(item -> item.getPrescriptionId().equals(prescription.getPrescriptionId()));
            prescriptions.add(prescription);
        }

        @Override
        public void deleteById(String prescriptionId) {
            prescriptions.removeIf(item -> item.getPrescriptionId().equals(prescriptionId));
        }

        @Override
        public List<Prescription> findByPatientId(String patientId) {
            return prescriptions.stream()
                    .filter(item -> item.getPatientId().equals(patientId))
                    .sorted(Comparator.comparing(Prescription::getDatePrescribed).reversed())
                    .toList();
        }

        @Override
        public List<Prescription> findAll() {
            return prescriptions.stream()
                    .sorted(Comparator.comparing(Prescription::getDatePrescribed).reversed())
                    .toList();
        }
    }

    private static final class FakeVisitRepository extends VisitRepository {
        private final List<Visit> visits;

        private FakeVisitRepository(List<Visit> visits) {
            super(UNUSED_CONNECTION);
            this.visits = new ArrayList<>(visits);
        }

        @Override
        public Optional<Visit> findById(VisitKey visitKey) {
            return visits.stream()
                    .filter(item -> item.getPatientId().equals(visitKey.patientId())
                            && item.getDoctorId().equals(visitKey.doctorId())
                            && item.getDateOfVisit().equals(visitKey.dateOfVisit()))
                    .findFirst();
        }

        @Override
        public void update(VisitKey originalKey, Visit visit) {
            visits.removeIf(item -> item.getPatientId().equals(originalKey.patientId())
                    && item.getDoctorId().equals(originalKey.doctorId())
                    && item.getDateOfVisit().equals(originalKey.dateOfVisit()));
            visits.add(visit);
        }

        @Override
        public List<Visit> findByPatientId(String patientId) {
            return visits.stream()
                    .filter(item -> item.getPatientId().equals(patientId))
                    .sorted(Comparator.comparing(Visit::getDateOfVisit).reversed())
                    .toList();
        }

        @Override
        public List<Visit> findAll() {
            return visits.stream()
                    .sorted(Comparator.comparing(Visit::getDateOfVisit).reversed())
                    .toList();
        }

        @Override
        public void deleteById(VisitKey visitKey) {
            visits.removeIf(item -> item.getPatientId().equals(visitKey.patientId())
                    && item.getDoctorId().equals(visitKey.doctorId())
                    && item.getDateOfVisit().equals(visitKey.dateOfVisit()));
        }
    }

    private static final class FakeClaimRepository extends ClaimRepository {
        private final List<Claim> claims;

        private FakeClaimRepository(List<Claim> claims) {
            super(UNUSED_CONNECTION);
            this.claims = new ArrayList<>(claims);
        }

        @Override
        public void save(Claim claim) {
            claims.add(claim);
        }

        @Override
        public void update(Claim claim) {
            claims.removeIf(item -> item.getClaimId().equals(claim.getClaimId()));
            claims.add(claim);
        }

        @Override
        public void deleteById(String claimId) {
            claims.removeIf(item -> item.getClaimId().equals(claimId));
        }

        @Override
        public Optional<Claim> findById(String claimId) {
            return claims.stream().filter(item -> item.getClaimId().equals(claimId)).findFirst();
        }

        @Override
        public Optional<Claim> findByPrescriptionId(String prescriptionId) {
            return claims.stream().filter(item -> item.getPrescriptionId().equals(prescriptionId)).findFirst();
        }

        @Override
        public List<Claim> findByPatientId(String patientId) {
            return claims.stream().filter(item -> item.getPatientId().equals(patientId)).toList();
        }

        @Override
        public List<Claim> findAll() {
            return List.copyOf(claims);
        }
    }
}
