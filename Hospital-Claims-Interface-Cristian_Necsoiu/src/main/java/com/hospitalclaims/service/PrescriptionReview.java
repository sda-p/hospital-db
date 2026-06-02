package com.hospitalclaims.service;

import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Drug;
import com.hospitalclaims.model.Prescription;

import java.time.LocalDate;
import java.util.List;

/** Prescription with derived eligibility, activity, and reference details. */
public record PrescriptionReview(
        Prescription prescription,
        Drug drug,
        Doctor doctor,
        LocalDate endDate,
        boolean active,
        boolean eligible,
        List<String> eligibilityIssues
) {
}
