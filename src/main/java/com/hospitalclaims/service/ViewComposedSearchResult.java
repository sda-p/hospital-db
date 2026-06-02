package com.hospitalclaims.service;

import java.util.List;

/** Combined search output spanning every dataset linked by one business key. */
public record ViewComposedSearchResult(
        ViewComposedQuery query,
        List<ViewSearchResult> sections
) {
    /** Counts all matching records across the composed result sections. */
    public int totalRecords() {
        return sections.stream().mapToInt(ViewSearchResult::recordCount).sum();
    }
}
