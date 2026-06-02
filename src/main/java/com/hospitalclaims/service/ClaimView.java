package com.hospitalclaims.service;

import com.hospitalclaims.model.Claim;

/** Claim plus its resolved prescription review context. */
public record ClaimView(
        Claim claim,
        PrescriptionReview prescriptionReview
) {
}
