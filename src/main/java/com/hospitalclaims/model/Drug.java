package com.hospitalclaims.model;

/** Drug catalogue entry used when recording prescriptions. */
public class Drug {
    private String drugId;
    private String drugName;
    private String sideEffects;
    private String purpose;

    public Drug() {
    }

    public Drug(String drugId, String drugName, String sideEffects, String purpose) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.sideEffects = sideEffects;
        this.purpose = purpose;
    }

    public String getDrugId() {
        return drugId;
    }

    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public String getSideEffects() {
        return sideEffects;
    }

    public void setSideEffects(String sideEffects) {
        this.sideEffects = sideEffects;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
