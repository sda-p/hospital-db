package com.hospitalclaims.service;

import java.util.Arrays;

/** Datasets exposed through the browser search explorer. */
public enum ViewDataset {
    PATIENTS("patients", "Patients"),
    DOCTORS("doctors", "Doctors"),
    DRUGS("drugs", "Drugs"),
    VISITS("visits", "Visits"),
    PRESCRIPTIONS("prescriptions", "Prescriptions"),
    CLAIMS("claims", "Claims"),
    CLAIM_REVIEW("claim-review", "Claim Review");

    private final String key;
    private final String label;

    ViewDataset(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Resolves a request key, defaulting to patients when no key is supplied. */
    public static ViewDataset fromKey(String key) {
        if (key == null || key.isBlank()) {
            return PATIENTS;
        }
        return Arrays.stream(values())
                .filter(value -> value.key.equalsIgnoreCase(key.trim()))
                .findFirst()
                .orElseThrow(() -> new SearchQueryException("Unknown dataset: " + key + "."));
    }
}
