package com.hospitalclaims.service;

/** Shared identifier families that allow records to be composed across datasets. */
public enum ViewKeyFamily {
    PATIENT_ID("patientId"),
    DOCTOR_ID("doctorId"),
    DRUG_ID("drugId"),
    PRESCRIPTION_ID("prescriptionId"),
    CLAIM_ID("claimId"),
    INSURANCE_ID("insuranceId");

    private final String columnKey;

    ViewKeyFamily(String columnKey) {
        this.columnKey = columnKey;
    }

    /** Returns the canonical column key for this identifier family. */
    public String columnKey() {
        return columnKey;
    }
}
