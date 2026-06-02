package com.hospitalclaims.model;

import java.time.LocalDate;

/** Prescription data captured for later review and claim processing. */
public class Prescription {
    private String prescriptionId;
    private LocalDate datePrescribed;
    private String dosage;
    private String duration;
    private String comment;
    private String drugId;
    private String doctorId;
    private String patientId;

    public Prescription() {
    }

    public Prescription(String prescriptionId, LocalDate datePrescribed, String dosage, String duration, String comment,
                        String drugId, String doctorId, String patientId) {
        this.prescriptionId = prescriptionId;
        this.datePrescribed = datePrescribed;
        this.dosage = dosage;
        this.duration = duration;
        this.comment = comment;
        this.drugId = drugId;
        this.doctorId = doctorId;
        this.patientId = patientId;
    }

    public String getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(String prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public LocalDate getDatePrescribed() {
        return datePrescribed;
    }

    public void setDatePrescribed(LocalDate datePrescribed) {
        this.datePrescribed = datePrescribed;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDrugId() {
        return drugId;
    }

    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
}
