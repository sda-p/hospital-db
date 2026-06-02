package com.hospitalclaims.service;

/** Predicate used when evaluating free-form dataset filters. */
interface ViewFilter {
    boolean matches(ViewRecord record);
}
