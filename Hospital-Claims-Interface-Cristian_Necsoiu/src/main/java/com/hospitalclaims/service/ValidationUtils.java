package com.hospitalclaims.service;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Shared input validation helpers for the service layer. */
public final class ValidationUtils {
    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ValidationUtils() {
    }

    /** Requires a non-empty string and returns its trimmed form. */
    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank.");
        }
        return value.trim();
    }

    /** Trims optional text while preserving null values. */
    public static String optionalTrimmed(String value) {
        return value == null ? null : value.trim();
    }

    /** Applies a deliberately simple email shape check for user input. */
    public static void requireEmail(String value, String fieldName) {
        if (value == null || value.isBlank() || !SIMPLE_EMAIL_PATTERN.matcher(value.trim()).matches()) {
            throw new ValidationException(fieldName + " must be a valid email address.");
        }
    }

    /** Requires a date to be present. */
    public static void requireDate(LocalDate value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " must not be null.");
        }
    }

    /** Parses a positive integer from user input. */
    public static int requirePositiveInteger(String value, String fieldName) {
        String trimmed = requireNonBlank(value, fieldName);
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed <= 0) {
                throw new ValidationException(fieldName + " must be a positive integer.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ValidationException(fieldName + " must be a positive integer.");
        }
    }
}
