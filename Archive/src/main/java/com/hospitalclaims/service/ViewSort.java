package com.hospitalclaims.service;

/** Sort directive parsed from the generic search syntax. */
public record ViewSort(
        String columnKey,
        boolean ascending
) {
}
