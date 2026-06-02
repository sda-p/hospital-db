package com.hospitalclaims.service;

/** Supported workflow states for a claim. */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    PAID
}
