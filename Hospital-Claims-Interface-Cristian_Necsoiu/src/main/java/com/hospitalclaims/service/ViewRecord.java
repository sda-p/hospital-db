package com.hospitalclaims.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable row abstraction used by the generic search UI. */
public final class ViewRecord {
    private final Map<String, Object> values;
    private final Map<String, Boolean> flags;

    public ViewRecord(Map<String, Object> values, Map<String, Boolean> flags) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
    }

    /** Returns the raw value map in display order. */
    public Map<String, Object> values() {
        return values;
    }

    /** Returns boolean flags derived during record loading. */
    public Map<String, Boolean> flags() {
        return flags;
    }

    /** Returns a raw typed value for sorting and formatting. */
    public Object rawValue(String key) {
        return values.get(key);
    }

    /** Normalizes values to strings for filter and export operations. */
    public String stringValue(String key) {
        Object value = values.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return String.valueOf(value);
    }

    /** Returns whether the requested derived flag is enabled. */
    public boolean flagValue(String key) {
        return Boolean.TRUE.equals(flags.get(key));
    }
}
