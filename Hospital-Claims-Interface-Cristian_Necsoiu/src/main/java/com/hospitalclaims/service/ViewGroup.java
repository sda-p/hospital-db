package com.hospitalclaims.service;

import java.util.List;
import java.util.Map;

/** Grouped view-search bucket and the records it contains. */
public record ViewGroup(
        Map<String, String> groupedValues,
        List<ViewRecord> records
) {
}
