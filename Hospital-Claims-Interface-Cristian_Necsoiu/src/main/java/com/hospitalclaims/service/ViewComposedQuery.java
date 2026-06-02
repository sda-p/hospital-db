package com.hospitalclaims.service;

/** Cross-dataset search request anchored on a shared business key. */
public record ViewComposedQuery(
        ViewDataset sourceDataset,
        String sourceColumnKey,
        String value,
        ViewKeyFamily keyFamily
) {
}
