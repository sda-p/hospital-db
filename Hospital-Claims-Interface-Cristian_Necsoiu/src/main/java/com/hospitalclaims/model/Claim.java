package com.hospitalclaims.model;

import java.time.LocalDate;

/** Insurance claim lifecycle data derived from a prescription. */
public class Claim {
    private String claimId;
    private String prescriptionId;
    private String patientId;
    private String insuranceId;
    private String status;
    private LocalDate createdDate;
    private LocalDate submittedDate;
    private String reviewedBy;
    private LocalDate decisionDate;
    private String decisionNotes;

    public Claim() {
    }

    public Claim(String claimId,
                 String prescriptionId,
                 String patientId,
                 String insuranceId,
                 String status,
                 LocalDate createdDate,
                 LocalDate submittedDate,
                 String reviewedBy,
                 LocalDate decisionDate,
                 String decisionNotes) {
        this.claimId = claimId;
        this.prescriptionId = prescriptionId;
        this.patientId = patientId;
        this.insuranceId = insuranceId;
        this.status = status;
        this.createdDate = createdDate;
        this.submittedDate = submittedDate;
        this.reviewedBy = reviewedBy;
        this.decisionDate = decisionDate;
        this.decisionNotes = decisionNotes;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(String prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getInsuranceId() {
        return insuranceId;
    }

    public void setInsuranceId(String insuranceId) {
        this.insuranceId = insuranceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDate submittedDate) {
        this.submittedDate = submittedDate;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDate getDecisionDate() {
        return decisionDate;
    }

    public void setDecisionDate(LocalDate decisionDate) {
        this.decisionDate = decisionDate;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }
}
