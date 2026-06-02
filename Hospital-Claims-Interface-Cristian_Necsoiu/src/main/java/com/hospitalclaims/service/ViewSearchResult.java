package com.hospitalclaims.service;

import java.util.List;

/** Search result page for one dataset, optionally grouped. */
public record ViewSearchResult(
        ViewQuery query,
        List<ViewColumn> columns,
        List<String> flags,
        List<ViewRecord> records,
        List<ViewGroup> groups,
        int totalRecords,
        int totalGroups,
        int page,
        int pageSize,
        int totalPages
) {
    /** Indicates whether this page represents grouped output rather than flat rows. */
    public boolean grouped() {
        return !query.groupColumns().isEmpty();
    }

    /** Returns the total matched row count before paging. */
    public int recordCount() {
        return totalRecords;
    }

    /** Indicates whether a previous result page exists. */
    public boolean hasPreviousPage() {
        return page > 1;
    }

    /** Indicates whether a later result page exists. */
    public boolean hasNextPage() {
        return page < totalPages;
    }
}
