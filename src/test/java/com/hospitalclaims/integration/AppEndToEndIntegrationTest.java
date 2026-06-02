package com.hospitalclaims.integration;

import com.hospitalclaims.App;
import com.hospitalclaims.model.Claim;
import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Prescription;
import com.hospitalclaims.model.Visit;
import com.hospitalclaims.repository.ClaimRepository;
import com.hospitalclaims.repository.DoctorRepository;
import com.hospitalclaims.repository.PatientRepository;
import com.hospitalclaims.repository.PrescriptionRepository;
import com.hospitalclaims.repository.VisitKey;
import com.hospitalclaims.repository.VisitRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppEndToEndIntegrationTest extends MysqlIntegrationTestSupport {
    @Test
    void menuDrivenCreateAndUpdateFlowsPersistToMysql() throws SQLException {
        seedBaseReferenceData();
        configureApplicationProperties();

        ByteArrayInputStream input = new ByteArrayInputStream(menuInput().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        java.io.InputStream originalIn = System.in;

        try {
            System.setIn(input);
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            App.main(new String[]{"menu"});
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            clearApplicationProperties();
        }

        PatientRepository patientRepository = new PatientRepository(createDatabaseConnection());
        DoctorRepository doctorRepository = new DoctorRepository(createDatabaseConnection());
        VisitRepository visitRepository = new VisitRepository(createDatabaseConnection());
        PrescriptionRepository prescriptionRepository = new PrescriptionRepository(createDatabaseConnection());
        ClaimRepository claimRepository = new ClaimRepository(createDatabaseConnection());

        Patient patient = patientRepository.findById("PAT900").orElseThrow();
        Doctor doctor = doctorRepository.findById("DOC900").orElseThrow();
        Visit visit = visitRepository.findById(new VisitKey("PAT900", "DOC900", java.time.LocalDate.of(2026, 5, 1))).orElseThrow();
        Prescription prescription = prescriptionRepository.findById("RX900").orElseThrow();
        Claim claim = claimRepository.findById("CLM900").orElseThrow();
        String consoleOutput = output.toString(StandardCharsets.UTF_8);

        assertEquals("Reed-Updated", patient.getSurname());
        assertEquals("5 Claim Street", patient.getAddress());
        assertNull(patient.getPhone());
        assertEquals("02079990001", doctor.getPhone());
        assertEquals("Neurology", doctor.getSpecialization());
        assertEquals("Follow-up symptoms", visit.getSymptoms());
        assertEquals("DX-101", visit.getDiagnosisId());
        assertEquals("2 tablets", prescription.getDosage());
        assertEquals("20", prescription.getDuration());
        assertEquals("Reviewed after follow-up", prescription.getComment());
        assertEquals("APPROVED", claim.getStatus());
        assertEquals("claims.reviewer@example.com", claim.getReviewedBy());
        assertTrue(consoleOutput.contains("Insurance provider updated: INS900"));
        assertTrue(consoleOutput.contains("Drug updated: DRUG900"));
        assertTrue(consoleOutput.contains("Patient updated: PAT900"));
        assertTrue(consoleOutput.contains("Prescription updated: RX900"));
        assertTrue(consoleOutput.contains("Claim approved: CLM900"));
        assertTrue(consoleOutput.contains("eligible yes"));
        assertTrue(consoleOutput.contains("issues none"));
    }

    private void seedBaseReferenceData() throws SQLException {
        com.hospitalclaims.repository.InsuranceRepository insuranceRepository =
                new com.hospitalclaims.repository.InsuranceRepository(createDatabaseConnection());
        com.hospitalclaims.repository.DrugRepository drugRepository =
                new com.hospitalclaims.repository.DrugRepository(createDatabaseConnection());

        insuranceRepository.save(new com.hospitalclaims.model.Insurance(
                "INS900", "Northwind Health", "1 Main St", "02070000000"
        ));
        drugRepository.save(new com.hospitalclaims.model.Drug(
                "DRUG900", "Amoxicillin", "Nausea", "Antibiotic"
        ));
    }

    private String menuInput() {
        return String.join("\n", List.of(
                "5",
                "DOC900",
                "Mira",
                "Lane",
                "4 Main St",
                "02070000001",
                "mira.lane@example.com",
                "Cardiology",
                "Central Hospital",
                "4",
                "PAT900",
                "Ava",
                "Reed",
                "AB1 2CD",
                "2 Main St",
                "07111111111",
                "ava.reed@example.com",
                "INS900",
                "DOC900",
                "3",
                "INS900",
                "Northwind Health Updated",
                "",
                "02079990009",
                "7",
                "DRUG900",
                "Amoxicillin XR",
                "-",
                "Extended antibiotic",
                "8",
                "PAT900",
                "DOC900",
                "2026-05-01",
                "Headache",
                "DX-100",
                "9",
                "RX900",
                "2026-05-01",
                "1 tablet",
                "10",
                "Initial review",
                "DRUG900",
                "DOC900",
                "PAT900",
                "10",
                "PAT900",
                "",
                "Reed-Updated",
                "",
                "5 Claim Street",
                "-",
                "",
                "",
                "",
                "11",
                "DOC900",
                "",
                "",
                "",
                "02079990001",
                "",
                "Neurology",
                "",
                "12",
                "PAT900",
                "DOC900",
                "2026-05-01",
                "",
                "",
                "",
                "Follow-up symptoms",
                "DX-101",
                "13",
                "RX900",
                "",
                "2 tablets",
                "20",
                "Reviewed after follow-up",
                "",
                "",
                "",
                "14",
                "CLM900",
                "RX900",
                "Initial claim notes",
                "15",
                "CLM900",
                "16",
                "CLM900",
                "claims.reviewer@example.com",
                "17",
                "CLM900",
                "claims.reviewer@example.com",
                "Approved after checks",
                "27",
                "PAT900",
                "19",
                "PAT900",
                "28",
                "PAT900",
                "33"
        )) + "\n";
    }
}
