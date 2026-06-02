package com.hospitalclaims.model;

/** Doctor master data referenced by visits, prescriptions, and patient assignments. */
public class Doctor {
    private String doctorId;
    private String firstName;
    private String surname;
    private String address;
    private String phone;
    private String email;
    private String specialization;
    private String hospital;

    public Doctor() {
    }

    public Doctor(String doctorId, String firstName, String surname, String address, String phone, String email,
                  String specialization, String hospital) {
        this.doctorId = doctorId;
        this.firstName = firstName;
        this.surname = surname;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.specialization = specialization;
        this.hospital = hospital;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
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

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getHospital() {
        return hospital;
    }

    public void setHospital(String hospital) {
        this.hospital = hospital;
    }
}
