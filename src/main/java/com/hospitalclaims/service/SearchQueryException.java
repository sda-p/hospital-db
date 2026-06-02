package com.hospitalclaims.service;

/** Raised when a view-search request cannot be parsed or executed as requested. */
public class SearchQueryException extends RuntimeException {
    public SearchQueryException(String message) {
        super(message);
    }
}
