package com.hospitalclaims.model;

/** Insurance provider details used by patient records and claims. */
public class Insurance {
    private String insuranceId;
    private String company;
    private String address;
    private String phone;

    public Insurance() {
    }

    public Insurance(String insuranceId, String company, String address, String phone) {
        this.insuranceId = insuranceId;
        this.company = company;
        this.address = address;
        this.phone = phone;
    }

    public String getInsuranceId() {
        return insuranceId;
    }

    public void setInsuranceId(String insuranceId) {
        this.insuranceId = insuranceId;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
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
}
