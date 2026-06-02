package com.hospitalclaims.model;

/** Patient master data used throughout claims and visit workflows. */
public class Patient {
    private String patientId;
    private String firstName;
    private String surname;
    private String postcode;
    private String address;
    private String phone;
    private String email;
    private String insuranceId;
    private String primaryCareDoctorId;

    public Patient() {
    }

    public Patient(String patientId, String firstName, String surname, String postcode, String address, String phone,
                   String email, String insuranceId, String primaryCareDoctorId) {
        this.patientId = patientId;
        this.firstName = firstName;
        this.surname = surname;
        this.postcode = postcode;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.insuranceId = insuranceId;
        this.primaryCareDoctorId = primaryCareDoctorId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInsuranceId() {
        return insuranceId;
    }

    public void setInsuranceId(String insuranceId) {
        this.insuranceId = insuranceId;
    }

    public String getPrimaryCareDoctorId() {
        return primaryCareDoctorId;
    }

    public void setPrimaryCareDoctorId(String primaryCareDoctorId) {
        this.primaryCareDoctorId = primaryCareDoctorId;
    }
}
