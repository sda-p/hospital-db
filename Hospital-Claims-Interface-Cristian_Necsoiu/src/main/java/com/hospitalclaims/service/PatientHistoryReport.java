package com.hospitalclaims.service;

import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Insurance;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Visit;

import java.util.List;

/** Aggregated patient history for console and browser reporting. */
public record PatientHistoryReport(
        Patient patient,
        Insurance insurance,
        Doctor primaryCareDoctor,
        List<Visit> visits,
        List<PrescriptionReview> prescriptions
) {
}
