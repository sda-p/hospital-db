package com.hospitalclaims.service;

/** Column metadata used by the generic search and grouping UI. */
public record ViewColumn(
        String key,
        String label,
        ViewKeyFamily keyFamily
) {
    public ViewColumn(String key, String label) {
        this(key, label, null);
    }

    /** Indicates whether this column can link records across datasets. */
    public boolean composable() {
        return keyFamily != null;
    }
}
