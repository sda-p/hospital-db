package com.hospitalclaims.model;

import java.time.LocalDate;

/** Clinical visit record keyed by patient, doctor, and visit date. */
public class Visit {
    private String patientId;
    private String doctorId;
    private LocalDate dateOfVisit;
    private String symptoms;
    private String diagnosisId;

    public Visit() {
    }

    public Visit(String patientId, String doctorId, LocalDate dateOfVisit, String symptoms, String diagnosisId) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.dateOfVisit = dateOfVisit;
        this.symptoms = symptoms;
        this.diagnosisId = diagnosisId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDate getDateOfVisit() {
        return dateOfVisit;
    }

    public void setDateOfVisit(LocalDate dateOfVisit) {
        this.dateOfVisit = dateOfVisit;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getDiagnosisId() {
        return diagnosisId;
    }

    public void setDiagnosisId(String diagnosisId) {
        this.diagnosisId = diagnosisId;
    }
}
