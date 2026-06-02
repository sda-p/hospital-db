package com.hospitalclaims.integration;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepositoryIntegrationTest extends MysqlIntegrationTestSupport {
    @Test
    void repositoriesPersistAndRetrieveLinkedRecordsAgainstMysql() throws SQLException {
        InsuranceRepository insuranceRepository = new InsuranceRepository(createDatabaseConnection());
        DoctorRepository doctorRepository = new DoctorRepository(createDatabaseConnection());
        DrugRepository drugRepository = new DrugRepository(createDatabaseConnection());
        PatientRepository patientRepository = new PatientRepository(createDatabaseConnection());
        VisitRepository visitRepository = new VisitRepository(createDatabaseConnection());
        PrescriptionRepository prescriptionRepository = new PrescriptionRepository(createDatabaseConnection());
        ClaimRepository claimRepository = new ClaimRepository(createDatabaseConnection());

        insuranceRepository.save(new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000"));
        doctorRepository.save(new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital"));
        drugRepository.save(new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic"));
        patientRepository.save(new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", "DOC1"));
        visitRepository.save(new Visit("PAT1", "DOC1", LocalDate.of(2026, 5, 1), "Headache", "DX-1"));
        prescriptionRepository.save(new Prescription("RX1", LocalDate.of(2026, 5, 1), "1 tablet", "10",
                "Take after food", "DRUG1", "DOC1", "PAT1"));
        claimRepository.save(new Claim("CLM1", "RX1", "PAT1", "INS1", "SUBMITTED",
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 2), null, null, "Queued"));

        Patient patient = patientRepository.findById("PAT1").orElseThrow();
        Visit visit = visitRepository.findById(new VisitKey("PAT1", "DOC1", LocalDate.of(2026, 5, 1))).orElseThrow();
        Prescription prescription = prescriptionRepository.findById("RX1").orElseThrow();
        Claim claim = claimRepository.findById("CLM1").orElseThrow();

        assertEquals("Ava", patient.getFirstName());
        assertEquals("Headache", visit.getSymptoms());
        assertEquals("Take after food", prescription.getComment());
        assertEquals("SUBMITTED", claim.getStatus());
        assertEquals(1, patientRepository.findBySurname("co").size());
        assertEquals(1, doctorRepository.findBySpecialization("card").size());
        assertEquals(1, drugRepository.findByName("moxi").size());
        assertEquals(1, visitRepository.findByPatientId("PAT1").size());
        assertEquals(1, prescriptionRepository.findByPatientId("PAT1").size());
        assertEquals(1, claimRepository.findByPatientId("PAT1").size());
    }

    @Test
    void repositoriesUpdateExistingRecordsAgainstMysql() throws SQLException {
        InsuranceRepository insuranceRepository = new InsuranceRepository(createDatabaseConnection());
        DoctorRepository doctorRepository = new DoctorRepository(createDatabaseConnection());
        DrugRepository drugRepository = new DrugRepository(createDatabaseConnection());
        PatientRepository patientRepository = new PatientRepository(createDatabaseConnection());
        VisitRepository visitRepository = new VisitRepository(createDatabaseConnection());
        PrescriptionRepository prescriptionRepository = new PrescriptionRepository(createDatabaseConnection());
        ClaimRepository claimRepository = new ClaimRepository(createDatabaseConnection());

        insuranceRepository.save(new Insurance("INS1", "Northwind Health", "1 Main St", "02070000000"));
        doctorRepository.save(new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02070000001",
                "mia.stone@example.com", "Cardiology", "Central Hospital"));
        doctorRepository.save(new Doctor("DOC2", "Noah", "Wells", "4 Main St", "02070000002",
                "noah.wells@example.com", "General Practice", "Community Clinic"));
        drugRepository.save(new Drug("DRUG1", "Amoxicillin", "Nausea", "Antibiotic"));
        patientRepository.save(new Patient("PAT1", "Ava", "Cole", "AB1 2CD", "2 Main St", "07111111111",
                "ava@example.com", "INS1", "DOC1"));
        visitRepository.save(new Visit("PAT1", "DOC1", LocalDate.of(2026, 5, 1), "Headache", "DX-1"));
        prescriptionRepository.save(new Prescription("RX1", LocalDate.of(2026, 5, 1), "1 tablet", "10",
                "Take after food", "DRUG1", "DOC1", "PAT1"));
        claimRepository.save(new Claim("CLM1", "RX1", "PAT1", "INS1", "SUBMITTED",
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 2), null, null, "Queued"));

        insuranceRepository.update(new Insurance("INS1", "Northwind Plus", null, "02079990000"));
        drugRepository.update(new Drug("DRUG1", "Amoxicillin XR", null, "Extended antibiotic"));
        patientRepository.update(new Patient("PAT1", "Ava", "Coleman", "AB1 2CD", "5 Main St", null,
                "ava.coleman@example.com", null, "DOC2"));
        doctorRepository.update(new Doctor("DOC1", "Mia", "Stone", "3 Main St", "02079990001",
                "mia.stone@example.com", "Neurology", "Central Hospital"));
        visitRepository.update(new VisitKey("PAT1", "DOC1", LocalDate.of(2026, 5, 1)),
                new Visit("PAT1", "DOC2", LocalDate.of(2026, 5, 3), "Improving", "DX-2"));
        prescriptionRepository.update(new Prescription("RX1", LocalDate.of(2026, 5, 3), "2 tablets", "20",
                "Reviewed after follow-up", "DRUG1", "DOC2", "PAT1"));
        claimRepository.update(new Claim("CLM1", "RX1", "PAT1", "INS1", "APPROVED",
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 2), "reviewer@example.com",
                LocalDate.of(2026, 5, 4), "Approved"));

        Insurance insurance = insuranceRepository.findById("INS1").orElseThrow();
        Drug drug = drugRepository.findById("DRUG1").orElseThrow();
        Patient patient = patientRepository.findById("PAT1").orElseThrow();
        Doctor doctor = doctorRepository.findById("DOC1").orElseThrow();
        Visit visit = visitRepository.findById(new VisitKey("PAT1", "DOC2", LocalDate.of(2026, 5, 3))).orElseThrow();
        Prescription prescription = prescriptionRepository.findById("RX1").orElseThrow();
        Claim claim = claimRepository.findById("CLM1").orElseThrow();

        assertEquals("Northwind Plus", insurance.getCompany());
        assertNull(insurance.getAddress());
        assertEquals("Amoxicillin XR", drug.getDrugName());
        assertNull(drug.getSideEffects());
        assertEquals("Coleman", patient.getSurname());
        assertEquals("5 Main St", patient.getAddress());
        assertNull(patient.getPhone());
        assertEquals("DOC2", patient.getPrimaryCareDoctorId());
        assertEquals("02079990001", doctor.getPhone());
        assertEquals("Neurology", doctor.getSpecialization());
        assertEquals("Improving", visit.getSymptoms());
        assertEquals("DX-2", visit.getDiagnosisId());
        assertEquals("2 tablets", prescription.getDosage());
        assertEquals("20", prescription.getDuration());
        assertEquals("Reviewed after follow-up", prescription.getComment());
        assertEquals("APPROVED", claim.getStatus());
        assertEquals("reviewer@example.com", claim.getReviewedBy());
    }
}
