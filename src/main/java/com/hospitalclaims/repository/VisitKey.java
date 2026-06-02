package com.hospitalclaims.repository;

import java.time.LocalDate;

/** Composite key used to address a visit row. */
public record VisitKey(String patientId, String doctorId, LocalDate dateOfVisit) {
}
