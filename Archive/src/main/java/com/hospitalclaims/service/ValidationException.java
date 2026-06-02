package com.hospitalclaims.service;

/** Raised when business validation rejects user-provided data. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
