package com.hospitalclaims.service;

import com.hospitalclaims.model.Claim;
import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Drug;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Visit;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/** Powers the generic browser search, grouping, and cross-dataset navigation views. */
public class ViewSearchService {
    private final HospitalClaimsService service;
    private final Map<ViewDataset, ViewDatasetDefinition> definitions;
    private final Map<ViewKeyFamily, List<ViewDataset>> composedDatasetMap;

    public ViewSearchService(HospitalClaimsService service) {
        this.service = service;
        this.definitions = buildDefinitions();
        this.composedDatasetMap = buildComposedDatasetMap();
    }

    /** Lists every dataset available to the browser explorer. */
    public List<ViewDataset> datasets() {
        return List.of(ViewDataset.values());
    }

    /** Returns the column and loader definition for one dataset. */
    public ViewDatasetDefinition definitionFor(ViewDataset dataset) {
        return definitions.get(dataset);
    }

    /** Indicates whether a column can drive composed cross-dataset search. */
    public boolean isComposableColumn(ViewDataset dataset, String columnKey) {
        return columnFor(definitionFor(dataset), columnKey).composable();
    }

    /** Searches every related dataset using a shared identifier value. */
    public ViewComposedSearchResult searchComposed(String sourceDatasetKey,
                                                   String sourceColumnKey,
                                                   String value) throws SQLException {
        ViewDataset sourceDataset = ViewDataset.fromKey(sourceDatasetKey);
        ViewDatasetDefinition sourceDefinition = definitionFor(sourceDataset);
        ViewColumn sourceColumn = columnFor(sourceDefinition, sourceColumnKey);
        if (!sourceColumn.composable()) {
            throw new SearchQueryException("Column " + sourceColumnKey + " is not available for composed search.");
        }
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isBlank()) {
            throw new SearchQueryException("Composed search value must not be blank.");
        }

        ViewComposedQuery composedQuery = new ViewComposedQuery(
                sourceDataset,
                sourceColumn.key(),
                normalizedValue,
                sourceColumn.keyFamily()
        );
        List<ViewDataset> orderedDatasets = orderedDatasetsFor(sourceDataset, sourceColumn.keyFamily());
        List<ViewSearchResult> sections = new ArrayList<>();
        for (ViewDataset dataset : orderedDatasets) {
            // Each section reuses the normal dataset search path so paging and formatting stay aligned.
            sections.add(searchDatasetForComposedKey(dataset, sourceColumn.keyFamily(), normalizedValue));
        }
        return new ViewComposedSearchResult(composedQuery, List.copyOf(sections));
    }

    /** Parses and executes a search request for one dataset page. */
    public ViewSearchResult search(String datasetKey,
                                   String rawQuery,
                                   String rawSort,
                                   String rawGroup,
                                   boolean groupSort,
                                   String rawPage,
                                   String rawPageSize) throws SQLException {
        ViewDataset dataset = ViewDataset.fromKey(datasetKey);
        ViewDatasetDefinition definition = definitionFor(dataset);
        ViewQuery query = parseQuery(definition, rawQuery, rawSort, rawGroup, groupSort, rawPage, rawPageSize);
        return executeSearch(definition, query);
    }

    /** Applies filters, optional grouping, sorting, and paging to one dataset. */
    private ViewSearchResult executeSearch(ViewDatasetDefinition definition,
                                           ViewQuery query) throws SQLException {
        List<ViewRecord> matched = definition.loadRecords().stream()
                .filter(record -> query.filters().stream().allMatch(filter -> filter.matches(record)))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!query.groupColumns().isEmpty()) {
            List<ViewGroup> groups = groupRecords(matched, query);
            List<ViewGroup> visibleGroups = pageSlice(groups, query.page(), query.pageSize());
            int totalPages = totalPages(groups.size(), query.pageSize());
            return new ViewSearchResult(
                    query,
                    definition.columns(),
                    definition.flags(),
                    matched,
                    visibleGroups,
                    matched.size(),
                    groups.size(),
                    query.page(),
                    query.pageSize(),
                    totalPages
            );
        }

        if (!query.sorts().isEmpty()) {
            matched.sort(comparatorFor(query.sorts()));
        }
        List<ViewRecord> visibleRecords = pageSlice(matched, query.page(), query.pageSize());
        int totalPages = totalPages(matched.size(), query.pageSize());
        return new ViewSearchResult(
                query,
                definition.columns(),
                definition.flags(),
                visibleRecords,
                List.of(),
                matched.size(),
                0,
                query.page(),
                query.pageSize(),
                totalPages
        );
    }

    /** Executes the same search without practical paging limits. */
    public ViewSearchResult searchAll(String datasetKey,
                                      String rawQuery,
                                      String rawSort,
                                      String rawGroup,
                                      boolean groupSort) throws SQLException {
        return search(datasetKey, rawQuery, rawSort, rawGroup, groupSort, "1", "1000000");
    }

    /** Normalizes raw request parameters into a validated query object. */
    private ViewQuery parseQuery(ViewDatasetDefinition definition,
                                 String rawQuery,
                                 String rawSort,
                                 String rawGroup,
                                 boolean groupSort,
                                 String rawPage,
                                 String rawPageSize) {
        List<ViewFilter> filters = parseFilters(definition, rawQuery);
        List<ViewSort> sorts = parseSorts(definition, rawSort);
        List<String> groupColumns = parseGroupColumns(definition, rawGroup);
        int pageSize = parsePositiveInt(rawPageSize, 25, "pageSize");
        int page = parsePositiveInt(rawPage, 1, "page");
        return new ViewQuery(
                definition.dataset(),
                rawQuery == null ? "" : rawQuery.trim(),
                rawSort == null ? "" : rawSort.trim(),
                rawGroup == null ? "" : rawGroup.trim(),
                groupSort,
                page,
                pageSize,
                filters,
                sorts,
                groupColumns
        );
    }

    /** Parses the free-form query syntax into executable predicates. */
    private List<ViewFilter> parseFilters(ViewDatasetDefinition definition, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        List<ViewFilter> filters = new ArrayList<>();
        for (String token : rawQuery.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (!token.contains(":")) {
                boolean negated = token.startsWith("!");
                String flagKey = negated ? token.substring(1) : token;
                if (!definition.flags().contains(flagKey)) {
                    throw new SearchQueryException("Unknown flag for " + definition.dataset().key() + ": " + flagKey + ".");
                }
                filters.add(record -> negated != record.flagValue(flagKey));
                continue;
            }

            String[] parts = token.split(":", 2);
            String columnKey = parts[0].trim();
            if (!definition.columnKeys().contains(columnKey)) {
                throw new SearchQueryException("Unknown column for " + definition.dataset().key() + ": " + columnKey + ".");
            }
            if (parts[1].startsWith("=")) {
                String expected = normalize(parts[1].substring(1));
                filters.add(record -> normalize(record.stringValue(columnKey)).equals(expected));
                continue;
            }
            if (parts[1].startsWith("re:")) {
                Pattern pattern = compileRegex(parts[1].substring(3));
                filters.add(record -> pattern.matcher(record.stringValue(columnKey)).find());
                continue;
            }
            if (parts[1].startsWith("/") && parts[1].endsWith("/") && parts[1].length() > 1) {
                Pattern pattern = compileRegex(parts[1].substring(1, parts[1].length() - 1));
                filters.add(record -> pattern.matcher(record.stringValue(columnKey)).find());
                continue;
            }

            String expected = parts[1].trim();
            if (expected.contains("*")) {
                // Wildcards match the full normalized field so users can express simple prefix/suffix patterns.
                Pattern pattern = compileWildcard(expected);
                filters.add(record -> pattern.matcher(record.stringValue(columnKey)).matches());
            } else {
                String normalizedExpected = normalize(expected);
                filters.add(record -> normalize(record.stringValue(columnKey)).contains(normalizedExpected));
            }
        }
        return List.copyOf(filters);
    }

    /** Parses sort directives in the form column or column:direction. */
    private List<ViewSort> parseSorts(ViewDatasetDefinition definition, String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return List.of();
        }
        List<ViewSort> sorts = new ArrayList<>();
        for (String directive : rawSort.split(",")) {
            String trimmed = directive.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            String columnKey = parts[0].trim();
            if (!definition.columnKeys().contains(columnKey)) {
                throw new SearchQueryException("Unknown sort column for " + definition.dataset().key() + ": " + columnKey + ".");
            }
            boolean ascending = true;
            if (parts.length == 2) {
                if ("asc".equalsIgnoreCase(parts[1].trim())) {
                    ascending = true;
                } else if ("desc".equalsIgnoreCase(parts[1].trim())) {
                    ascending = false;
                } else {
                    throw new SearchQueryException("Invalid sort direction for " + columnKey + ": " + parts[1] + ".");
                }
            }
            sorts.add(new ViewSort(columnKey, ascending));
        }
        return List.copyOf(sorts);
    }

    /** Parses the comma-separated list of grouping columns. */
    private List<String> parseGroupColumns(ViewDatasetDefinition definition, String rawGroup) {
        if (rawGroup == null || rawGroup.isBlank()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String token : rawGroup.split(",")) {
            String columnKey = token.trim();
            if (columnKey.isBlank()) {
                continue;
            }
            if (!definition.columnKeys().contains(columnKey)) {
                throw new SearchQueryException("Unknown group column for " + definition.dataset().key() + ": " + columnKey + ".");
            }
            columns.add(columnKey);
        }
        return List.copyOf(columns);
    }

    /** Buckets matched rows by the configured group columns. */
    private List<ViewGroup> groupRecords(List<ViewRecord> matched,
                                         ViewQuery query) {
        Map<List<String>, List<ViewRecord>> grouped = new LinkedHashMap<>();
        for (ViewRecord record : matched) {
            List<String> key = query.groupColumns().stream()
                    .map(record::stringValue)
                    .toList();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }

        Comparator<List<String>> groupComparator = Comparator.comparing(
                values -> values.stream().map(ViewSearchService::normalize).collect(Collectors.joining("\u0000"))
        );
        List<Map.Entry<List<String>, List<ViewRecord>>> entries = new ArrayList<>(grouped.entrySet());
        entries.sort(Map.Entry.comparingByKey(groupComparator));

        List<ViewGroup> groups = new ArrayList<>();
        Comparator<ViewRecord> rowComparator = query.sorts().isEmpty() ? null : comparatorFor(query.sorts());
        for (Map.Entry<List<String>, List<ViewRecord>> entry : entries) {
            List<ViewRecord> records = new ArrayList<>(entry.getValue());
            if (query.groupSort() && rowComparator != null) {
                // Group-sort only affects row ordering inside each bucket once groups have been formed.
                records.sort(rowComparator);
            }
            Map<String, String> groupedValues = new LinkedHashMap<>();
            for (int index = 0; index < query.groupColumns().size(); index++) {
                String columnKey = query.groupColumns().get(index);
                groupedValues.put(columnKey, entry.getKey().get(index));
            }
            groups.add(new ViewGroup(groupedValues, List.copyOf(records)));
        }
        return List.copyOf(groups);
    }

    /** Parses a positive integer with a default fallback when the input is blank. */
    private int parsePositiveInt(String rawValue, int defaultValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            if (parsed < 1) {
                throw new SearchQueryException(fieldName + " must be greater than zero.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new SearchQueryException(fieldName + " must be a whole number.");
        }
    }

    /** Returns the requested page slice without throwing on short final pages. */
    private static <T> List<T> pageSlice(List<T> items, int page, int pageSize) {
        int startIndex = Math.max(0, (page - 1) * pageSize);
        if (startIndex >= items.size()) {
            return List.of();
        }
        int endIndex = Math.min(items.size(), startIndex + pageSize);
        return List.copyOf(items.subList(startIndex, endIndex));
    }

    /** Computes the total number of pages for a result set. */
    private static int totalPages(int itemCount, int pageSize) {
        return Math.max(1, (int) Math.ceil((double) itemCount / pageSize));
    }

    /** Builds a comparator that evaluates sort directives in order. */
    private Comparator<ViewRecord> comparatorFor(List<ViewSort> sorts) {
        return (left, right) -> {
            for (ViewSort sort : sorts) {
                int result = compareValues(left.rawValue(sort.columnKey()), right.rawValue(sort.columnKey()));
                if (result != 0) {
                    return sort.ascending() ? result : -result;
                }
            }
            return 0;
        };
    }

    /** Compares values while handling nulls, dates, and plain strings consistently. */
    private static int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        if (left instanceof LocalDate leftDate && right instanceof LocalDate rightDate) {
            return leftDate.compareTo(rightDate);
        }
        return normalize(String.valueOf(left)).compareTo(normalize(String.valueOf(right)));
    }

    /** Converts a wildcard token into a case-insensitive regular expression. */
    private Pattern compileWildcard(String value) {
        StringBuilder regex = new StringBuilder("^");
        for (char character : value.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /** Compiles a user-supplied regex and wraps syntax failures in a query exception. */
    private Pattern compileRegex(String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException exception) {
            throw new SearchQueryException("Invalid regex: " + exception.getDescription() + ".");
        }
    }

    /** Trims and lowercases strings before textual comparisons. */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /** Declares the available datasets, their columns, and their record loaders. */
    private Map<ViewDataset, ViewDatasetDefinition> buildDefinitions() {
        Map<ViewDataset, ViewDatasetDefinition> map = new LinkedHashMap<>();
        map.put(ViewDataset.PATIENTS, new ViewDatasetDefinition(
                ViewDataset.PATIENTS,
                List.of(
                        new ViewColumn("patientId", "Patient", ViewKeyFamily.PATIENT_ID),
                        new ViewColumn("firstName", "First name"),
                        new ViewColumn("surname", "Surname"),
                        new ViewColumn("fullName", "Full name"),
                        new ViewColumn("postcode", "Postcode"),
                        new ViewColumn("email", "Email"),
                        new ViewColumn("phone", "Phone"),
                        new ViewColumn("insuranceId", "Insurance", ViewKeyFamily.INSURANCE_ID),
                        new ViewColumn("primaryCareDoctorId", "Primary doctor", ViewKeyFamily.DOCTOR_ID)
                ),
                List.of("hasInsurance", "hasPrimaryCareDoctor"),
                this::loadPatientRecords
        ));
        map.put(ViewDataset.DOCTORS, new ViewDatasetDefinition(
                ViewDataset.DOCTORS,
                List.of(
                        new ViewColumn("doctorId", "Doctor", ViewKeyFamily.DOCTOR_ID),
                        new ViewColumn("firstName", "First name"),
                        new ViewColumn("surname", "Surname"),
                        new ViewColumn("fullName", "Full name"),
                        new ViewColumn("specialization", "Specialization"),
                        new ViewColumn("hospital", "Hospital"),
                        new ViewColumn("email", "Email"),
                        new ViewColumn("phone", "Phone")
                ),
                List.of("hasHospital", "hasPhone"),
                this::loadDoctorRecords
        ));
        map.put(ViewDataset.DRUGS, new ViewDatasetDefinition(
                ViewDataset.DRUGS,
                List.of(
                        new ViewColumn("drugId", "Drug", ViewKeyFamily.DRUG_ID),
                        new ViewColumn("drugName", "Name"),
                        new ViewColumn("purpose", "Purpose"),
                        new ViewColumn("sideEffects", "Side effects")
                ),
                List.of(),
                this::loadDrugRecords
        ));
        map.put(ViewDataset.VISITS, new ViewDatasetDefinition(
                ViewDataset.VISITS,
                List.of(
                        new ViewColumn("patientId", "Patient", ViewKeyFamily.PATIENT_ID),
                        new ViewColumn("doctorId", "Doctor", ViewKeyFamily.DOCTOR_ID),
                        new ViewColumn("dateOfVisit", "Visit date"),
                        new ViewColumn("symptoms", "Symptoms"),
                        new ViewColumn("diagnosisId", "Diagnosis")
                ),
                List.of("hasDiagnosis"),
                this::loadVisitRecords
        ));
        map.put(ViewDataset.PRESCRIPTIONS, new ViewDatasetDefinition(
                ViewDataset.PRESCRIPTIONS,
                List.of(
                        new ViewColumn("prescriptionId", "Prescription", ViewKeyFamily.PRESCRIPTION_ID),
                        new ViewColumn("patientId", "Patient", ViewKeyFamily.PATIENT_ID),
                        new ViewColumn("doctorId", "Doctor", ViewKeyFamily.DOCTOR_ID),
                        new ViewColumn("doctorName", "Doctor name"),
                        new ViewColumn("drugId", "Drug", ViewKeyFamily.DRUG_ID),
                        new ViewColumn("drugName", "Drug name"),
                        new ViewColumn("datePrescribed", "Start"),
                        new ViewColumn("endDate", "End"),
                        new ViewColumn("dosage", "Dosage"),
                        new ViewColumn("duration", "Duration"),
                        new ViewColumn("comment", "Comment"),
                        new ViewColumn("eligibilityIssues", "Issues")
                ),
                List.of("active", "eligible", "hasComment"),
                this::loadPrescriptionRecords
        ));
        map.put(ViewDataset.CLAIMS, new ViewDatasetDefinition(
                ViewDataset.CLAIMS,
                List.of(
                        new ViewColumn("claimId", "Claim", ViewKeyFamily.CLAIM_ID),
                        new ViewColumn("patientId", "Patient", ViewKeyFamily.PATIENT_ID),
                        new ViewColumn("insuranceId", "Insurance", ViewKeyFamily.INSURANCE_ID),
                        new ViewColumn("prescriptionId", "Prescription", ViewKeyFamily.PRESCRIPTION_ID),
                        new ViewColumn("status", "Status"),
                        new ViewColumn("createdDate", "Created"),
                        new ViewColumn("submittedDate", "Submitted"),
                        new ViewColumn("reviewedBy", "Reviewer"),
                        new ViewColumn("decisionDate", "Decision"),
                        new ViewColumn("decisionNotes", "Decision notes"),
                        new ViewColumn("eligibilityIssues", "Issues")
                ),
                List.of("eligible", "activePrescription", "reviewed"),
                this::loadClaimRecords
        ));
        map.put(ViewDataset.CLAIM_REVIEW, new ViewDatasetDefinition(
                ViewDataset.CLAIM_REVIEW,
                List.of(
                        new ViewColumn("prescriptionId", "Prescription", ViewKeyFamily.PRESCRIPTION_ID),
                        new ViewColumn("patientId", "Patient", ViewKeyFamily.PATIENT_ID),
                        new ViewColumn("doctorId", "Doctor", ViewKeyFamily.DOCTOR_ID),
                        new ViewColumn("doctorName", "Doctor name"),
                        new ViewColumn("drugId", "Drug", ViewKeyFamily.DRUG_ID),
                        new ViewColumn("drugName", "Drug name"),
                        new ViewColumn("datePrescribed", "Start"),
                        new ViewColumn("endDate", "End"),
                        new ViewColumn("dosage", "Dosage"),
                        new ViewColumn("duration", "Duration"),
                        new ViewColumn("comment", "Comment"),
                        new ViewColumn("eligibilityIssues", "Issues")
                ),
                List.of("active", "eligible", "hasComment"),
                this::loadClaimReviewRecords
        ));
        return Map.copyOf(map);
    }

    /** Precomputes which datasets participate in each shared identifier family. */
    private Map<ViewKeyFamily, List<ViewDataset>> buildComposedDatasetMap() {
        Map<ViewKeyFamily, List<ViewDataset>> map = new LinkedHashMap<>();
        map.put(ViewKeyFamily.PATIENT_ID, List.of(
                ViewDataset.PATIENTS,
                ViewDataset.VISITS,
                ViewDataset.PRESCRIPTIONS,
                ViewDataset.CLAIMS,
                ViewDataset.CLAIM_REVIEW
        ));
        map.put(ViewKeyFamily.DOCTOR_ID, List.of(
                ViewDataset.DOCTORS,
                ViewDataset.VISITS,
                ViewDataset.PRESCRIPTIONS,
                ViewDataset.CLAIM_REVIEW
        ));
        map.put(ViewKeyFamily.DRUG_ID, List.of(
                ViewDataset.DRUGS,
                ViewDataset.PRESCRIPTIONS,
                ViewDataset.CLAIM_REVIEW
        ));
        map.put(ViewKeyFamily.PRESCRIPTION_ID, List.of(
                ViewDataset.PRESCRIPTIONS,
                ViewDataset.CLAIMS,
                ViewDataset.CLAIM_REVIEW
        ));
        map.put(ViewKeyFamily.CLAIM_ID, List.of(ViewDataset.CLAIMS));
        map.put(ViewKeyFamily.INSURANCE_ID, List.of(
                ViewDataset.PATIENTS,
                ViewDataset.CLAIMS
        ));
        return Map.copyOf(map);
    }

    /** Orders composed-search sections with the source dataset first. */
    private List<ViewDataset> orderedDatasetsFor(ViewDataset sourceDataset, ViewKeyFamily keyFamily) {
        List<ViewDataset> datasets = new ArrayList<>(composedDatasetMap.getOrDefault(keyFamily, List.of()));
        if (datasets.remove(sourceDataset)) {
            datasets.add(0, sourceDataset);
        }
        return List.copyOf(datasets);
    }

    /** Executes a normal search constrained to the shared key-family column. */
    private ViewSearchResult searchDatasetForComposedKey(ViewDataset dataset,
                                                         ViewKeyFamily keyFamily,
                                                         String value) throws SQLException {
        ViewDatasetDefinition definition = definitionFor(dataset);
        String columnKey = keyFamily.columnKey();
        columnFor(definition, columnKey);
        String rawQuery = columnKey + ":=" + value;
        ViewQuery query = new ViewQuery(
                dataset,
                rawQuery,
                "",
                "",
                false,
                1,
                1_000_000,
                List.of(record -> normalize(record.stringValue(columnKey)).equals(normalize(value))),
                List.of(),
                List.of()
        );
        return executeSearch(definition, query);
    }

    /** Resolves column metadata or fails with a dataset-specific message. */
    private ViewColumn columnFor(ViewDatasetDefinition definition, String columnKey) {
        return definition.columns().stream()
                .filter(column -> column.key().equals(columnKey))
                .findFirst()
                .orElseThrow(() -> new SearchQueryException("Unknown column for "
                        + definition.dataset().key() + ": " + columnKey + "."));
    }

    private List<ViewRecord> loadPatientRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (Patient patient : service.getAllPatients()) {
            records.add(record(values(
                            "patientId", patient.getPatientId(),
                            "firstName", patient.getFirstName(),
                            "surname", patient.getSurname(),
                            "fullName", joinName(patient.getFirstName(), patient.getSurname()),
                            "postcode", patient.getPostcode(),
                            "email", patient.getEmail(),
                            "phone", patient.getPhone(),
                            "insuranceId", patient.getInsuranceId(),
                            "primaryCareDoctorId", patient.getPrimaryCareDoctorId()
                    ),
                    Map.of(
                            "hasInsurance", hasText(patient.getInsuranceId()),
                            "hasPrimaryCareDoctor", hasText(patient.getPrimaryCareDoctorId())
                    )));
        }
        return List.copyOf(records);
    }

    private List<ViewRecord> loadDoctorRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (Doctor doctor : service.getAllDoctors()) {
            records.add(record(values(
                            "doctorId", doctor.getDoctorId(),
                            "firstName", doctor.getFirstName(),
                            "surname", doctor.getSurname(),
                            "fullName", joinName(doctor.getFirstName(), doctor.getSurname()),
                            "specialization", doctor.getSpecialization(),
                            "hospital", doctor.getHospital(),
                            "email", doctor.getEmail(),
                            "phone", doctor.getPhone()
                    ),
                    Map.of(
                            "hasHospital", hasText(doctor.getHospital()),
                            "hasPhone", hasText(doctor.getPhone())
                    )));
        }
        return List.copyOf(records);
    }

    private List<ViewRecord> loadDrugRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (Drug drug : service.getAllDrugs()) {
            records.add(record(values(
                            "drugId", drug.getDrugId(),
                            "drugName", drug.getDrugName(),
                            "purpose", drug.getPurpose(),
                            "sideEffects", drug.getSideEffects()
                    ),
                    Map.of()));
        }
        return List.copyOf(records);
    }

    private List<ViewRecord> loadVisitRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (Visit visit : service.getAllVisits()) {
            records.add(record(values(
                            "patientId", visit.getPatientId(),
                            "doctorId", visit.getDoctorId(),
                            "dateOfVisit", visit.getDateOfVisit(),
                            "symptoms", visit.getSymptoms(),
                            "diagnosisId", visit.getDiagnosisId()
                    ),
                    Map.of(
                            "hasDiagnosis", hasText(visit.getDiagnosisId())
                    )));
        }
        return List.copyOf(records);
    }

    private List<ViewRecord> loadPrescriptionRecords() throws SQLException {
        return loadPrescriptionReviewRecords();
    }

    private List<ViewRecord> loadClaimReviewRecords() throws SQLException {
        return loadPrescriptionReviewRecords();
    }

    private List<ViewRecord> loadPrescriptionReviewRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (PrescriptionReview review : service.getPrescriptionReviews()) {
            records.add(record(values(
                            "prescriptionId", review.prescription().getPrescriptionId(),
                            "patientId", review.prescription().getPatientId(),
                            "doctorId", review.prescription().getDoctorId(),
                            "doctorName", review.doctor() == null
                                    ? review.prescription().getDoctorId()
                                    : joinName(review.doctor().getFirstName(), review.doctor().getSurname()),
                            "drugId", review.prescription().getDrugId(),
                            "drugName", review.drug() == null
                                    ? review.prescription().getDrugId()
                                    : review.drug().getDrugName(),
                            "datePrescribed", review.prescription().getDatePrescribed(),
                            "endDate", review.endDate(),
                            "dosage", review.prescription().getDosage(),
                            "duration", review.prescription().getDuration(),
                            "comment", review.prescription().getComment(),
                            "eligibilityIssues", joinIssues(review.eligibilityIssues())
                    ),
                    Map.of(
                            "active", review.active(),
                            "eligible", review.eligible(),
                            "hasComment", hasText(review.prescription().getComment())
                    )));
        }
        return List.copyOf(records);
    }

    private List<ViewRecord> loadClaimRecords() throws SQLException {
        List<ViewRecord> records = new ArrayList<>();
        for (ClaimView view : service.getClaimViews()) {
            Claim claim = view.claim();
            records.add(record(values(
                            "claimId", claim.getClaimId(),
                            "patientId", claim.getPatientId(),
                            "insuranceId", claim.getInsuranceId(),
                            "prescriptionId", claim.getPrescriptionId(),
                            "status", claim.getStatus(),
                            "createdDate", claim.getCreatedDate(),
                            "submittedDate", claim.getSubmittedDate(),
                            "reviewedBy", claim.getReviewedBy(),
                            "decisionDate", claim.getDecisionDate(),
                            "decisionNotes", claim.getDecisionNotes(),
                            "eligibilityIssues", joinIssues(view.prescriptionReview().eligibilityIssues())
                    ),
                    Map.of(
                            "eligible", view.prescriptionReview().eligible(),
                            "activePrescription", view.prescriptionReview().active(),
                            "reviewed", hasText(claim.getReviewedBy())
                    )));
        }
        return List.copyOf(records);
    }

    private static ViewRecord record(Map<String, Object> values, Map<String, Boolean> flags) {
        Map<String, Object> normalizedValues = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            // Blank strings are normalized to null so empty data behaves consistently across filters and views.
            normalizedValues.put(entry.getKey(), sanitize(entry.getValue()));
        }
        return new ViewRecord(normalizedValues, flags);
    }

    private static Map<String, Object> values(Object... items) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < items.length; index += 2) {
            values.put((String) items[index], items[index + 1]);
        }
        return values;
    }

    private static Object sanitize(Object value) {
        if (value instanceof String stringValue) {
            return stringValue.isBlank() ? null : stringValue;
        }
        return value;
    }

    private static String joinName(String firstName, String surname) {
        return String.join(" ", List.of(firstName, surname).stream()
                .filter(ViewSearchService::hasText)
                .toList());
    }

    private static String joinIssues(Collection<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "None";
        }
        return String.join("; ", issues);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Immutable dataset definition used by the search runtime. */
    public record ViewDatasetDefinition(
            ViewDataset dataset,
            List<ViewColumn> columns,
            List<String> flags,
            DatasetLoader loader
    ) {
        /** Returns the allowed column keys while preserving display order. */
        public Set<String> columnKeys() {
            return columns.stream().map(ViewColumn::key).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** Loads every record for this dataset on demand. */
        public List<ViewRecord> loadRecords() throws SQLException {
            return loader.load();
        }
    }

    @FunctionalInterface
    /** Functional loader used by dataset definitions. */
    public interface DatasetLoader {
        List<ViewRecord> load() throws SQLException;
    }
}
