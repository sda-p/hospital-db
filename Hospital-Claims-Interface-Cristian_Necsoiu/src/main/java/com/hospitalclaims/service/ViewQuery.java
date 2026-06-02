package com.hospitalclaims.service;

import java.util.List;

/** Parsed search request ready for execution against a dataset definition. */
public record ViewQuery(
        ViewDataset dataset,
        String rawQuery,
        String rawSort,
        String rawGroup,
        boolean groupSort,
        int page,
        int pageSize,
        List<ViewFilter> filters,
        List<ViewSort> sorts,
        List<String> groupColumns
) {
}
