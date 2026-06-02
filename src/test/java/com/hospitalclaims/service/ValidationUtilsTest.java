package com.hospitalclaims.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {
    @Test
    void requireNonBlankTrimsValue() {
        assertEquals("ABC123", ValidationUtils.requireNonBlank("  ABC123  ", "patientId"));
    }

    @Test
    void requireNonBlankRejectsNull() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> ValidationUtils.requireNonBlank(null, "patientId")
        );

        assertEquals("patientId must not be blank.", exception.getMessage());
    }

    @Test
    void requireEmailRejectsInvalidEmail() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> ValidationUtils.requireEmail("invalid-email", "email")
        );

        assertEquals("email must be a valid email address.", exception.getMessage());
    }

    @Test
    void requireDateRejectsNull() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> ValidationUtils.requireDate(null, "dateOfVisit")
        );

        assertEquals("dateOfVisit must not be null.", exception.getMessage());
    }

    @Test
    void requireDateAcceptsPresentValue() {
        ValidationUtils.requireDate(LocalDate.of(2026, 5, 8), "dateOfVisit");
    }

    @Test
    void requirePositiveIntegerRejectsNonNumericValue() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> ValidationUtils.requirePositiveInteger("abc", "duration")
        );

        assertEquals("duration must be a positive integer.", exception.getMessage());
    }

    @Test
    void requirePositiveIntegerRejectsZero() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> ValidationUtils.requirePositiveInteger("0", "duration")
        );

        assertEquals("duration must be a positive integer.", exception.getMessage());
    }

    @Test
    void requirePositiveIntegerParsesTrimmedValue() {
        assertEquals(14, ValidationUtils.requirePositiveInteger(" 14 ", "duration"));
    }
}
