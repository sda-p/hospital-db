package com.hospitalclaims.service;

import com.hospitalclaims.TestSupport;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewSearchServiceTest {
    private final ViewSearchService searchService = new ViewSearchService(TestSupport.sampleService());

    @Test
    void filtersPatientsWithWildcardAndFlags() throws SQLException {
        ViewSearchResult result = searchService.search("patients", "hasInsurance surname:co*", "surname:asc", null, false, null, null);

        assertEquals(1, result.recordCount());
        assertEquals("PAT1", result.records().getFirst().stringValue("patientId"));
    }

    @Test
    void groupsClaimsAndSortsWithinEachGroup() throws SQLException {
        ViewSearchResult result = searchService.search(
                "claims",
                null,
                "createdDate:desc",
                "patientId,status",
                true,
                null,
                null
        );

        assertEquals(2, result.groups().size());
        assertEquals("PAT1", result.groups().getFirst().groupedValues().get("patientId"));
        assertEquals("APPROVED", result.groups().getFirst().groupedValues().get("status"));
        assertEquals("SUBMITTED", result.groups().get(1).groupedValues().get("status"));
        assertEquals("CLM2", result.groups().get(1).records().getFirst().stringValue("claimId"));
    }

    @Test
    void supportsRegexSearchAgainstDoctorEmail() throws SQLException {
        ViewSearchResult result = searchService.search("doctors", "email:re:^mia.+@example\\.com$", null, null, false, null, null);

        assertEquals(1, result.recordCount());
        assertEquals("DOC1", result.records().getFirst().stringValue("doctorId"));
    }

    @Test
    void exposesVisitsDatasetWithDiagnosisFlag() throws SQLException {
        ViewSearchResult result = searchService.search("visits", "hasDiagnosis symptoms:follow*", "dateOfVisit:desc", null, false, null, null);

        assertEquals(1, result.recordCount());
        assertEquals("PAT1", result.records().getFirst().stringValue("patientId"));
        assertEquals("DX-2", result.records().getFirst().stringValue("diagnosisId"));
    }

    @Test
    void exposesClaimReviewAsSeparateDataset() throws SQLException {
        ViewSearchResult result = searchService.search("claim-review", "eligible", "datePrescribed:desc", null, false, null, null);

        assertEquals(1, result.recordCount());
        assertEquals("RX1", result.records().getFirst().stringValue("prescriptionId"));
        assertEquals("None", result.records().getFirst().stringValue("eligibilityIssues"));
    }

    @Test
    void rejectsInvalidRegex() {
        SearchQueryException exception = assertThrows(
                SearchQueryException.class,
                () -> searchService.search("patients", "surname:re:*invalid", null, null, false, null, null)
        );

        assertTrue(exception.getMessage().startsWith("Invalid regex:"));
    }

    @Test
    void paginatesFlatResults() throws SQLException {
        ViewSearchResult result = searchService.search("patients", null, "patientId:asc", null, false, "2", "1");

        assertEquals(2, result.recordCount());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.records().size());
        assertEquals("PAT2", result.records().getFirst().stringValue("patientId"));
    }

    @Test
    void composesSearchAcrossDatasetsForPatientId() throws SQLException {
        ViewComposedSearchResult result = searchService.searchComposed("claims", "patientId", "PAT1");

        assertEquals("PAT1", result.query().value());
        assertEquals(ViewDataset.CLAIMS, result.sections().getFirst().query().dataset());
        assertEquals(5, result.sections().size());
        assertEquals(9, result.totalRecords());
        assertEquals(2, result.sections().getFirst().recordCount());
        assertEquals(ViewDataset.PATIENTS, result.sections().get(1).query().dataset());
        assertEquals(1, result.sections().get(1).recordCount());
    }

    @Test
    void rejectsComposedSearchOnNonComposableColumn() {
        SearchQueryException exception = assertThrows(
                SearchQueryException.class,
                () -> searchService.searchComposed("patients", "surname", "Cole")
        );

        assertEquals("Column surname is not available for composed search.", exception.getMessage());
    }
}
