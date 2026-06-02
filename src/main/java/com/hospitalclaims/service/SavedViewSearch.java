package com.hospitalclaims.service;

/** Persisted browser search preset for the view explorer. */
public record SavedViewSearch(
        String name,
        String dataset,
        String query,
        String sort,
        String group,
        boolean groupSort,
        int pageSize
) {
}
