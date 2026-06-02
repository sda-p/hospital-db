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
import com.hospitalclaims.repository.VisitKey;
import com.hospitalclaims.repository.VisitRepository;
import com.hospitalclaims.service.ClaimStatus;
import com.hospitalclaims.service.HospitalClaimsService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TestSupport {
    private static final DatabaseConnection UNUSED_CONNECTION = new DatabaseConnection();

    private TestSupport() {
    }

    public static HospitalClaimsService sampleService() {
        LocalDate today = LocalDate.now();

        Insurance insurance = new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000");
        Patient insuredPatient = new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", "DOC1");
        Patient uninsuredPatient = new Patient("PAT2", "Liam", "Stone", "XY9 9ZZ", "8 Side St", null,
                "liam@example.com", null, null);

        Doctor doctorOne = new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital");
        Doctor doctorTwo = new Doctor("DOC2", "Noah", "Wells", "4 Main St", null,
                "noah.wells@example.com", "General Practice", null);

        Drug drugOne = new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic");
        Drug drugTwo = new Drug("DRUG2", "Ibuprofen", "Drowsiness", "Pain relief");

        Visit visitOne = new Visit("PAT1", "DOC1", today.minusDays(4), "Check-up", "DX-1");
        Visit visitTwo = new Visit("PAT1", "DOC1", today.minusDays(2), "Follow-up", "DX-2");

        Prescription prescriptionOne = new Prescription("RX1", today.minusDays(3), "1 tablet", "10",
                "Take after food", "DRUG1", "DOC1", "PAT1");
        Prescription prescriptionTwo = new Prescription("RX2", today.minusDays(1), "2 tablets", "45",
                null, "DRUG2", "DOC1", "PAT1");

        Claim approvedClaim = new Claim("CLM1", "RX1", "PAT1", "INS1", ClaimStatus.APPROVED.name(),
                today.minusDays(3), today.minusDays(2), "reviewer@example.com", today.minusDays(1), "Approved");
        Claim submittedClaim = new Claim("CLM2", "RX2", "PAT1", "INS1", ClaimStatus.SUBMITTED.name(),
                today.minusDays(1), today, null, null, null);

        return new HospitalClaimsService(
                new FakeClaimRepository(List.of(approvedClaim, submittedClaim)),
                new FakeInsuranceRepository(List.of(insurance)),
                new FakePatientRepository(List.of(insuredPatient, uninsuredPatient)),
                new FakeDoctorRepository(List.of(doctorOne, doctorTwo)),
                new FakeDrugRepository(List.of(drugOne, drugTwo)),
                new FakePrescriptionRepository(List.of(prescriptionOne, prescriptionTwo)),
                new FakeVisitRepository(List.of(visitOne, visitTwo))
        );
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
        public List<Patient> findAll() {
            return List.copyOf(patients);
        }

        @Override
        public void update(Patient patient) {
            for (int index = 0; index < patients.size(); index++) {
                if (patients.get(index).getPatientId().equals(patient.getPatientId())) {
                    patients.set(index, patient);
                    return;
                }
            }
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
        public List<Doctor> findAll() {
            return List.copyOf(doctors);
        }

        @Override
        public void update(Doctor doctor) {
            for (int index = 0; index < doctors.size(); index++) {
                if (doctors.get(index).getDoctorId().equals(doctor.getDoctorId())) {
                    doctors.set(index, doctor);
                    return;
                }
            }
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
        public List<Drug> findAll() {
            return List.copyOf(drugs);
        }

        @Override
        public void update(Drug drug) {
            for (int index = 0; index < drugs.size(); index++) {
                if (drugs.get(index).getDrugId().equals(drug.getDrugId())) {
                    drugs.set(index, drug);
                    return;
                }
            }
        }
    }

    private static final class FakePrescriptionRepository extends PrescriptionRepository {
        private final List<Prescription> prescriptions;

        private FakePrescriptionRepository(List<Prescription> prescriptions) {
            super(UNUSED_CONNECTION);
            this.prescriptions = new ArrayList<>(prescriptions);
        }

        @Override
        public Optional<Prescription> findById(String prescriptionId) {
            return prescriptions.stream().filter(item -> item.getPrescriptionId().equals(prescriptionId)).findFirst();
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

        @Override
        public void update(Prescription prescription) {
            for (int index = 0; index < prescriptions.size(); index++) {
                if (prescriptions.get(index).getPrescriptionId().equals(prescription.getPrescriptionId())) {
                    prescriptions.set(index, prescription);
                    return;
                }
            }
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
        public void update(VisitKey originalKey, Visit visit) {
            for (int index = 0; index < visits.size(); index++) {
                Visit current = visits.get(index);
                if (current.getPatientId().equals(originalKey.patientId())
                        && current.getDoctorId().equals(originalKey.doctorId())
                        && current.getDateOfVisit().equals(originalKey.dateOfVisit())) {
                    visits.set(index, visit);
                    return;
                }
            }
        }
    }

    private static final class FakeClaimRepository extends ClaimRepository {
        private final List<Claim> claims;

        private FakeClaimRepository(List<Claim> claims) {
            super(UNUSED_CONNECTION);
            this.claims = new ArrayList<>(claims);
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
