package com.hospitalclaims;

import com.hospitalclaims.model.Claim;
import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Drug;
import com.hospitalclaims.model.Insurance;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Prescription;
import com.hospitalclaims.model.Visit;
import com.hospitalclaims.service.ClaimView;
import com.hospitalclaims.service.HospitalClaimsService;
import com.hospitalclaims.service.PatientHistoryReport;
import com.hospitalclaims.service.PrescriptionReview;
import com.hospitalclaims.service.SavedViewSearch;
import com.hospitalclaims.service.SavedViewSearchStore;
import com.hospitalclaims.service.SearchQueryException;
import com.hospitalclaims.service.ValidationException;
import com.hospitalclaims.service.ViewColumn;
import com.hospitalclaims.service.ViewComposedSearchResult;
import com.hospitalclaims.service.ViewDataset;
import com.hospitalclaims.service.ViewGroup;
import com.hospitalclaims.service.ViewQuery;
import com.hospitalclaims.service.ViewRecord;
import com.hospitalclaims.service.ViewSearchResult;
import com.hospitalclaims.service.ViewSearchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/** Lightweight HTTP interface for the operational dashboard and search tooling. */
public class HospitalClaimsHttpServer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<Integer> VIEW_PAGE_SIZES = List.of(10, 25, 50, 100);

    private final HospitalClaimsService service;
    private final ViewSearchService viewSearchService;
    private final SavedViewSearchStore savedViewSearchStore;
    private final HttpServer server;

    /** Creates the server with the default saved-search backing file. */
    public HospitalClaimsHttpServer(HospitalClaimsService service, int port) throws IOException {
        this(service, port, new SavedViewSearchStore(Path.of("view-saved-searches.properties")));
    }

    /** Creates the server with an explicit saved-search store. */
    public HospitalClaimsHttpServer(HospitalClaimsService service, int port, SavedViewSearchStore savedViewSearchStore) throws IOException {
        this.service = service;
        this.viewSearchService = new ViewSearchService(service);
        this.savedViewSearchStore = savedViewSearchStore;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        configureRoutes();
    }

    /** Starts accepting HTTP requests. */
    public void start() {
        server.start();
    }

    /** Stops the server after the requested delay. */
    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    /** Returns the port actually bound by the server. */
    public int getPort() {
        return server.getAddress().getPort();
    }

    /** Registers page and form-action routes. */
    private void configureRoutes() {
        server.createContext("/", this::handleDashboard);
        server.createContext("/records", this::handleRecords);
        server.createContext("/claims", this::handleClaims);
        server.createContext("/reviews", this::handleReviews);
        server.createContext("/view", this::handleView);
        server.createContext("/view/export", this::handleViewExport);
        server.createContext("/delete", this::handleDeleteView);
        server.createContext("/login", this::handleLogin);
        server.createContext("/Login.html", this::handleLogin);
        server.createContext("/error", this::handleError);
        server.createContext("/Error.html", this::handleError);
        server.createContext("/actions/login", this::handleLoginAction);

        server.createContext("/actions/register-insurance", exchange -> handleAction(exchange, "/records", this::registerInsurance));
        server.createContext("/actions/update-insurance", exchange -> handleAction(exchange, "/records", this::updateInsurance));
        server.createContext("/actions/register-patient", exchange -> handleAction(exchange, "/records", this::registerPatient));
        server.createContext("/actions/update-patient", exchange -> handleAction(exchange, "/records", this::updatePatient));
        server.createContext("/actions/register-doctor", exchange -> handleAction(exchange, "/records", this::registerDoctor));
        server.createContext("/actions/update-doctor", exchange -> handleAction(exchange, "/records", this::updateDoctor));
        server.createContext("/actions/register-drug", exchange -> handleAction(exchange, "/records", this::registerDrug));
        server.createContext("/actions/update-drug", exchange -> handleAction(exchange, "/records", this::updateDrug));
        server.createContext("/actions/record-visit", exchange -> handleAction(exchange, "/records", this::recordVisit));
        server.createContext("/actions/update-visit", exchange -> handleAction(exchange, "/records", this::updateVisit));
        server.createContext("/actions/record-prescription", exchange -> handleAction(exchange, "/records", this::recordPrescription));
        server.createContext("/actions/update-prescription", exchange -> handleAction(exchange, "/records", this::updatePrescription));

        server.createContext("/actions/create-claim", exchange -> handleAction(exchange, "/claims", this::createClaim));
        server.createContext("/actions/submit-claim", exchange -> handleAction(exchange, "/claims", this::submitClaim));
        server.createContext("/actions/review-claim", exchange -> handleAction(exchange, "/claims", this::reviewClaim));
        server.createContext("/actions/approve-claim", exchange -> handleAction(exchange, "/claims", this::approveClaim));
        server.createContext("/actions/reject-claim", exchange -> handleAction(exchange, "/claims", this::rejectClaim));
        server.createContext("/actions/view-inline-edit", this::handleViewInlineEditAction);
        server.createContext("/actions/save-view-search", this::handleSaveViewSearchAction);
        server.createContext("/actions/delete-view-search", this::handleDeleteViewSearchAction);

        server.createContext("/actions/delete", exchange -> handleAction(exchange, "/delete", this::deleteEntity));
    }

    /** Renders the operational summary dashboard. */
    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        try {
            String body = renderFlash(query)
                    + priorityBanner("Operational status", "Browser control surface active")
                    + "<div class=\"dashboard-grid\">"
                    + statCard("Insurance providers", String.valueOf(service.getAllInsuranceProviders().size()), "Coverage records currently available")
                    + statCard("Patients", String.valueOf(service.getAllPatients().size()), "Registered patient records")
                    + statCard("Doctors", String.valueOf(service.getAllDoctors().size()), "Clinical staff records")
                    + statCard("Drugs", String.valueOf(service.getAllDrugs().size()), "Medication reference entries")
                    + statCard("Prescriptions", String.valueOf(service.getAllPrescriptions().size()), "Prescriptions on file")
                    + statCard("Visits", String.valueOf(service.getAllVisits().size()), "Encounter history records")
                    + statCard("Claims", String.valueOf(service.getAllClaims().size()), "Claims in all statuses")
                    + "</div>"
                    + workflowSection("Workflow map",
                    node("Records workspace", "Create and update insurance, patients, doctors, drugs, visits, and prescriptions.",
                            actionLink("/records", "Open records workspace"))
                            + node("Claims workspace", "Create drafts, move claims through review, and monitor the queue.",
                            actionLink("/claims", "Open claims workspace"))
                            + node("Investigations", "Run patient history, eligibility review, and search workflows.",
                            actionLink("/reviews", "Open investigations"))
                            + node("View workspace", "Search records with filters, wildcard matching, regex, sorting, and grouping.",
                            actionLink("/view", "Open view workspace"))
                            + node("Destructive actions", "Delete records with explicit confirmation in an isolated view.",
                            actionLink("/delete", "Open delete console")));
            sendHtml(exchange, 200, renderPage("Dashboard", "Hospital claims operations console", "Overview", query, body));
        } catch (SQLException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    private void handleRecords(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        String body = renderFlash(query)
                + priorityBanner("Records workspace", "Registration and maintenance")
                + workflowSection("Create records",
                cardGrid(
                        formCard("Register insurance", "/actions/register-insurance",
                                textField("insuranceId", "Insurance ID", true),
                                textField("company", "Company", true),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                submitButton("Create insurance")),
                        formCard("Register patient", "/actions/register-patient",
                                textField("patientId", "Patient ID", true),
                                textField("firstName", "First name", true),
                                textField("surname", "Surname", true),
                                textField("postcode", "Postcode", false),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                textField("email", "Email", true),
                                textField("insuranceId", "Insurance ID", false),
                                textField("primaryCareDoctorId", "Primary care doctor ID", false),
                                submitButton("Create patient")),
                        formCard("Register doctor", "/actions/register-doctor",
                                textField("doctorId", "Doctor ID", true),
                                textField("firstName", "First name", true),
                                textField("surname", "Surname", true),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                textField("email", "Email", true),
                                textField("specialization", "Specialization", false),
                                textField("hospital", "Hospital", false),
                                submitButton("Create doctor")),
                        formCard("Register drug", "/actions/register-drug",
                                textField("drugId", "Drug ID", true),
                                textField("drugName", "Drug name", true),
                                textArea("sideEffects", "Side effects"),
                                textArea("purpose", "Purpose"),
                                submitButton("Create drug")),
                        formCard("Record visit", "/actions/record-visit",
                                textField("patientId", "Patient ID", true),
                                textField("doctorId", "Doctor ID", true),
                                dateField("dateOfVisit", "Visit date", true),
                                textArea("symptoms", "Symptoms"),
                                textField("diagnosisId", "Diagnosis ID", false),
                                submitButton("Record visit")),
                        formCard("Record prescription", "/actions/record-prescription",
                                textField("prescriptionId", "Prescription ID", true),
                                dateField("datePrescribed", "Date prescribed", true),
                                textField("dosage", "Dosage", true),
                                textField("duration", "Duration (days)", true),
                                textArea("comment", "Clinical comment"),
                                textField("drugId", "Drug ID", true),
                                textField("doctorId", "Doctor ID", true),
                                textField("patientId", "Patient ID", true),
                                submitButton("Record prescription"))))
                + workflowSection("Update records",
                cardGrid(
                        formCard("Update insurance", "/actions/update-insurance",
                                textField("insuranceId", "Insurance ID", true),
                                textField("company", "Company", true),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                submitButton("Update insurance")),
                        formCard("Update patient", "/actions/update-patient",
                                textField("patientId", "Patient ID", true),
                                textField("firstName", "First name", true),
                                textField("surname", "Surname", true),
                                textField("postcode", "Postcode", false),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                textField("email", "Email", true),
                                textField("insuranceId", "Insurance ID", false),
                                textField("primaryCareDoctorId", "Primary care doctor ID", false),
                                submitButton("Update patient")),
                        formCard("Update doctor", "/actions/update-doctor",
                                textField("doctorId", "Doctor ID", true),
                                textField("firstName", "First name", true),
                                textField("surname", "Surname", true),
                                textArea("address", "Address"),
                                textField("phone", "Phone", false),
                                textField("email", "Email", true),
                                textField("specialization", "Specialization", false),
                                textField("hospital", "Hospital", false),
                                submitButton("Update doctor")),
                        formCard("Update drug", "/actions/update-drug",
                                textField("drugId", "Drug ID", true),
                                textField("drugName", "Drug name", true),
                                textArea("sideEffects", "Side effects"),
                                textArea("purpose", "Purpose"),
                                submitButton("Update drug")),
                        formCard("Update visit", "/actions/update-visit",
                                textField("originalPatientId", "Original patient ID", true),
                                textField("originalDoctorId", "Original doctor ID", true),
                                dateField("originalDateOfVisit", "Original visit date", true),
                                textField("patientId", "New patient ID", true),
                                textField("doctorId", "New doctor ID", true),
                                dateField("dateOfVisit", "New visit date", true),
                                textArea("symptoms", "Symptoms"),
                                textField("diagnosisId", "Diagnosis ID", false),
                                submitButton("Update visit")),
                        formCard("Update prescription", "/actions/update-prescription",
                                textField("prescriptionId", "Prescription ID", true),
                                dateField("datePrescribed", "Date prescribed", true),
                                textField("dosage", "Dosage", true),
                                textField("duration", "Duration (days)", true),
                                textArea("comment", "Clinical comment"),
                                textField("drugId", "Drug ID", true),
                                textField("doctorId", "Doctor ID", true),
                                textField("patientId", "Patient ID", true),
                                submitButton("Update prescription"))));
        sendHtml(exchange, 200, renderPage("Records", "Hospital claims operations console", "Registration and maintenance", query, body));
    }

    private void handleClaims(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        try {
            String patientId = trimToNull(query.get("patientId"));
            List<ClaimView> claims = patientId == null
                    ? service.getClaimViews()
                    : service.getClaimViewsForPatient(patientId);

            String body = renderFlash(query)
                    + priorityBanner("Claim workflow", patientId == null ? "Showing all claims" : "Filtered to patient " + escapeHtml(patientId))
                    + workflowSection("Actions",
                    cardGrid(
                            formCard("Create claim draft", "/actions/create-claim",
                                    textField("claimId", "Claim ID", true),
                                    textField("prescriptionId", "Prescription ID", true),
                                    textArea("notes", "Initial notes"),
                                    submitButton("Create draft")),
                            formCard("Submit claim", "/actions/submit-claim",
                                    textField("claimId", "Claim ID", true),
                                    submitButton("Submit claim")),
                            formCard("Move to under review", "/actions/review-claim",
                                    textField("claimId", "Claim ID", true),
                                    textField("reviewedBy", "Reviewer", true),
                                    submitButton("Start review")),
                            formCard("Approve claim", "/actions/approve-claim",
                                    textField("claimId", "Claim ID", true),
                                    textField("reviewedBy", "Reviewer", true),
                                    textArea("decisionNotes", "Decision notes"),
                                    submitButton("Approve claim")),
                            formCard("Reject claim", "/actions/reject-claim",
                                    textField("claimId", "Claim ID", true),
                                    textField("reviewedBy", "Reviewer", true),
                                    textArea("decisionNotes", "Decision notes"),
                                    submitButton("Reject claim")),
                            getFormCard("Queue filter", "/claims",
                                    textFieldWithValue("patientId", "Patient ID", patientId, false),
                                    submitButton("Apply filter"))))
                    + workflowSection("Queue",
                    tableNode("Claim queue", claims.isEmpty()
                                    ? "<p class=\"empty-state\">No claims found for the current filter.</p>"
                                    : table(
                                    List.of("Claim", "Status", "Patient", "Insurance", "Prescription", "Created", "Submitted", "Reviewer", "Decision", "Eligibility", "Issues"),
                                    claims.stream().map(view -> List.of(
                                            safeText(view.claim().getClaimId()),
                                            statusPill(view.claim().getStatus()),
                                            safeText(view.claim().getPatientId()),
                                            safeText(view.claim().getInsuranceId()),
                                            safeText(view.claim().getPrescriptionId()),
                                            safeText(formatDate(view.claim().getCreatedDate())),
                                            safeText(formatDate(view.claim().getSubmittedDate())),
                                            safeText(view.claim().getReviewedBy()),
                                            safeText(formatDate(view.claim().getDecisionDate())),
                                            view.prescriptionReview().eligible() ? "<span class=\"pill pill-ok\">Eligible</span>" : "<span class=\"pill pill-warn\">Needs review</span>",
                                            safeText(joinIssues(view.prescriptionReview().eligibilityIssues()))
                                    )).toList())));
            sendHtml(exchange, 200, renderPage("Claims", "Hospital claims operations console", "Claim handling", query, body));
        } catch (SQLException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    private void handleReviews(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        try {
            String panel = query.getOrDefault("panel", "history");
            StringBuilder body = new StringBuilder();
            body.append(renderFlash(query));
            body.append(priorityBanner("Investigations", "History, review, and search tools"));
            body.append(workflowSection("Query controls",
                    cardGrid(
                            getFormCard("Patient history", "/reviews",
                                    hiddenField("panel", "history"),
                                    textFieldWithValue("patientId", "Patient ID", query.get("patientId"), true),
                                    submitButton("Load history")),
                            getFormCard("Prescription review", "/reviews",
                                    hiddenField("panel", "claim-review"),
                                    textFieldWithValue("patientId", "Patient ID", query.get("patientId"), false),
                                    submitButton("Load review")),
                            getFormCard("Search patients", "/reviews",
                                    hiddenField("panel", "patients"),
                                    textFieldWithValue("surname", "Surname fragment", query.get("surname"), true),
                                    submitButton("Find patients")),
                            getFormCard("Search doctors", "/reviews",
                                    hiddenField("panel", "doctors"),
                                    textFieldWithValue("specialization", "Specialization fragment", query.get("specialization"), true),
                                    submitButton("Find doctors")),
                            getFormCard("Search drugs", "/reviews",
                                    hiddenField("panel", "drugs"),
                                    textFieldWithValue("drugName", "Drug name fragment", query.get("drugName"), true),
                                    submitButton("Find drugs")))));

            body.append(workflowSection("Results", switch (panel) {
                case "claim-review" -> renderPrescriptionReviewResults(trimToNull(query.get("patientId")));
                case "patients" -> renderPatientSearchResults(trimToNull(query.get("surname")));
                case "doctors" -> renderDoctorSearchResults(trimToNull(query.get("specialization")));
                case "drugs" -> renderDrugSearchResults(trimToNull(query.get("drugName")));
                default -> renderPatientHistoryResults(trimToNull(query.get("patientId")));
            }));

            sendHtml(exchange, 200, renderPage("Investigations", "Hospital claims operations console", "Clinical and operational review", query, body.toString()));
        } catch (SQLException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    /** Renders the generic search and grouping workspace. */
    private void handleView(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        Map<String, String> appliedQuery = query;
        String datasetKey = trimToNull(query.get("dataset"));
        String rawFilter = trimToNull(query.get("query"));
        String rawSort = trimToNull(query.get("sort"));
        String rawGroup = trimToNull(query.get("group"));
        String rawPage = trimToNull(query.get("page"));
        String rawPageSize = trimToNull(query.get("pageSize"));
        boolean groupSort = "on".equalsIgnoreCase(query.get("groupSort"))
                || "true".equalsIgnoreCase(query.get("groupSort"));

        try {
            appliedQuery = applySavedSearch(query);
            if ("on".equalsIgnoreCase(appliedQuery.get("composed"))
                    || "true".equalsIgnoreCase(appliedQuery.get("composed"))) {
                String sourceDatasetKey = trimToNull(appliedQuery.get("sourceDataset"));
                String sourceColumnKey = trimToNull(appliedQuery.get("sourceColumn"));
                String value = trimToNull(appliedQuery.get("value"));
                ViewComposedSearchResult result = viewSearchService.searchComposed(sourceDatasetKey, sourceColumnKey, value);
                String body = renderFlash(query)
                        + priorityBanner("View workspace", "Composed record retrieval")
                        + workflowSection("Query controls", cardGrid(
                        renderViewQueryCard(result.query().sourceDataset(), "", "", "", false, "1", "25"),
                        renderViewFieldCatalog(result.query().sourceDataset())
                ))
                        + workflowSection("Results", renderComposedViewResults(result));
                sendHtml(exchange, 200, renderPage("View", "Hospital claims operations console", "Search and retrieval", appliedQuery, body));
                return;
            }
            datasetKey = trimToNull(appliedQuery.get("dataset"));
            rawFilter = trimToNull(appliedQuery.get("query"));
            rawSort = trimToNull(appliedQuery.get("sort"));
            rawGroup = trimToNull(appliedQuery.get("group"));
            rawPage = trimToNull(appliedQuery.get("page"));
            rawPageSize = trimToNull(appliedQuery.get("pageSize"));
            groupSort = "on".equalsIgnoreCase(appliedQuery.get("groupSort"))
                    || "true".equalsIgnoreCase(appliedQuery.get("groupSort"));
            ViewSearchResult result = viewSearchService.search(datasetKey, rawFilter, rawSort, rawGroup, groupSort, rawPage, rawPageSize);
            String body = renderFlash(query)
                    + priorityBanner("View workspace", "Record retrieval and grouping")
                    + workflowSection("Query controls", cardGrid(
                    renderViewQueryCard(result),
                    renderViewFieldCatalog(result)
            ))
                    + workflowSection("Saved searches", renderSavedSearches(result.query()))
                    + workflowSection("Results", renderViewResults(result));
            sendHtml(exchange, 200, renderPage("View", "Hospital claims operations console", "Search and retrieval", appliedQuery, body));
        } catch (SearchQueryException exception) {
            ViewDataset dataset;
            try {
                dataset = ViewDataset.fromKey(datasetKey);
            } catch (SearchQueryException ignored) {
                dataset = ViewDataset.PATIENTS;
            }
            String body = renderFlash(Map.of("error", exception.getMessage()))
                    + priorityBanner("View workspace", "Record retrieval and grouping")
                    + workflowSection("Query controls", cardGrid(
                    renderViewQueryCard(dataset, rawFilter, rawSort, rawGroup, groupSort, rawPage, rawPageSize),
                    renderViewFieldCatalog(dataset)
            ))
                    + workflowSection("Saved searches", renderSavedSearches(
                    new ViewQuery(dataset,
                            rawFilter == null ? "" : rawFilter,
                            rawSort == null ? "" : rawSort,
                            rawGroup == null ? "" : rawGroup,
                            groupSort,
                            safePositiveInt(rawPage, 1),
                            safePositiveInt(rawPageSize, 25),
                            List.of(),
                            List.of(),
                            List.of())
            ))
                    + workflowSection("Results",
                    "<p class=\"empty-state\">Correct the query and run the search again.</p>");
            sendHtml(exchange, 200, renderPage("View", "Hospital claims operations console", "Search and retrieval", query, body));
        } catch (SQLException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    /** Streams the current view result as a downloadable export. */
    private void handleViewExport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());

        try {
            Map<String, String> appliedQuery = applySavedSearch(query);
            String format = trimToNull(appliedQuery.get("format"));
            if (format == null) {
                format = "csv";
            }
            ViewSearchResult result = viewSearchService.searchAll(
                    trimToNull(appliedQuery.get("dataset")),
                    trimToNull(appliedQuery.get("query")),
                    trimToNull(appliedQuery.get("sort")),
                    trimToNull(appliedQuery.get("group")),
                    "on".equalsIgnoreCase(appliedQuery.get("groupSort"))
                            || "true".equalsIgnoreCase(appliedQuery.get("groupSort"))
            );
            if ("json".equalsIgnoreCase(format)) {
                sendDownload(exchange, "application/json; charset=utf-8", "view-export.json", renderViewJson(result));
                return;
            }
            if ("csv".equalsIgnoreCase(format)) {
                sendDownload(exchange, "text/csv; charset=utf-8", "view-export.csv", renderViewCsv(result));
                return;
            }
            throw new SearchQueryException("Unknown export format: " + format + ".");
        } catch (SearchQueryException exception) {
            redirect(exchange, buildViewLocation(query, Map.of("error", exception.getMessage())));
        } catch (SQLException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    private void handleDeleteView(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        query.put("__path", exchange.getRequestURI().getPath());
        String body = renderFlash(query)
                + priorityBanner("Delete console", "Hard deletes require explicit confirmation")
                + workflowSection("Delete records",
                formCard("Destructive action", "/actions/delete",
                        "<p class=\"advisory\">Dependent records must be removed first. Enter <code>DELETE</code> exactly to continue.</p>",
                        selectField("entityType", "Entity", List.of(
                                option("insurance", "Insurance"),
                                option("patient", "Patient"),
                                option("doctor", "Doctor"),
                                option("drug", "Drug"),
                                option("visit", "Visit"),
                                option("prescription", "Prescription"),
                                option("claim", "Claim"))),
                        textField("id", "Primary ID", false),
                        textField("visitPatientId", "Visit patient ID", false),
                        textField("visitDoctorId", "Visit doctor ID", false),
                        dateField("visitDate", "Visit date", false),
                        textField("confirmation", "Confirmation text", true),
                        submitButton("Delete record")));
        sendHtml(exchange, 200, renderPage("Delete", "Hospital claims operations console", "Restricted operations", query, body));
    }

    /** Applies inline edits originating from the browser view workspace. */
    private void handleViewInlineEditAction(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            updateViewRecord(form);
            redirect(exchange, buildViewLocation(viewStateFromForm(form), Map.of(
                    "message", "Updated " + ViewDataset.fromKey(form.get("dataset")).label().toLowerCase() + " record."
            )));
        } catch (SQLException | RuntimeException exception) {
            redirect(exchange, buildViewLocation(viewStateFromForm(form), Map.of("error", exception.getMessage())));
        }
    }

    private void handleSaveViewSearchAction(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            String savedSearchName = form.get("savedSearchName");
            saveViewSearch(form);
            redirect(exchange, buildViewLocation(form, Map.of(
                    "message", "Saved search \"" + savedSearchName.trim() + "\".",
                    "page", "1"
            )));
        } catch (RuntimeException exception) {
            redirect(exchange, buildViewLocation(form, Map.of("error", exception.getMessage())));
        }
    }

    private void handleDeleteViewSearchAction(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            String name = form.get("name");
            deleteViewSearch(form);
            redirect(exchange, buildViewLocation(Map.of(), Map.of("message", "Deleted saved search \"" + name.trim() + "\".")));
        } catch (RuntimeException exception) {
            redirect(exchange, buildViewLocation(Map.of(), Map.of("error", exception.getMessage())));
        }
    }

    /** Handles standard POST actions and redirects with a flash message. */
    private void handleAction(HttpExchange exchange, String successPath, Action action) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        try {
            Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            action.run(form);
            redirect(exchange, successPath + "?message=" + urlEncode("Action completed successfully."));
        } catch (SQLException | RuntimeException exception) {
            redirect(exchange, successPath + "?error=" + urlEncode(exception.getMessage()));
        }
    }

    private void registerInsurance(Map<String, String> form) throws SQLException {
        Insurance insurance = new Insurance(
                form.get("insuranceId"),
                form.get("company"),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone"))
        );
        service.registerInsurance(insurance);
    }

    private void updateInsurance(Map<String, String> form) throws SQLException {
        Insurance insurance = new Insurance(
                form.get("insuranceId"),
                form.get("company"),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone"))
        );
        service.updateInsurance(insurance);
    }

    private void registerPatient(Map<String, String> form) throws SQLException {
        Patient patient = new Patient(
                form.get("patientId"),
                form.get("firstName"),
                form.get("surname"),
                emptyToNull(form.get("postcode")),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone")),
                form.get("email"),
                emptyToNull(form.get("insuranceId")),
                emptyToNull(form.get("primaryCareDoctorId"))
        );
        service.registerPatient(patient);
    }

    private void updatePatient(Map<String, String> form) throws SQLException {
        Patient patient = new Patient(
                form.get("patientId"),
                form.get("firstName"),
                form.get("surname"),
                emptyToNull(form.get("postcode")),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone")),
                form.get("email"),
                emptyToNull(form.get("insuranceId")),
                emptyToNull(form.get("primaryCareDoctorId"))
        );
        service.updatePatient(patient);
    }

    private void registerDoctor(Map<String, String> form) throws SQLException {
        Doctor doctor = new Doctor(
                form.get("doctorId"),
                form.get("firstName"),
                form.get("surname"),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone")),
                form.get("email"),
                emptyToNull(form.get("specialization")),
                emptyToNull(form.get("hospital"))
        );
        service.registerDoctor(doctor);
    }

    private void updateDoctor(Map<String, String> form) throws SQLException {
        Doctor doctor = new Doctor(
                form.get("doctorId"),
                form.get("firstName"),
                form.get("surname"),
                emptyToNull(form.get("address")),
                emptyToNull(form.get("phone")),
                form.get("email"),
                emptyToNull(form.get("specialization")),
                emptyToNull(form.get("hospital"))
        );
        service.updateDoctor(doctor);
    }

    private void registerDrug(Map<String, String> form) throws SQLException {
        Drug drug = new Drug(
                form.get("drugId"),
                form.get("drugName"),
                emptyToNull(form.get("sideEffects")),
                emptyToNull(form.get("purpose"))
        );
        service.registerDrug(drug);
    }

    private void updateDrug(Map<String, String> form) throws SQLException {
        Drug drug = new Drug(
                form.get("drugId"),
                form.get("drugName"),
                emptyToNull(form.get("sideEffects")),
                emptyToNull(form.get("purpose"))
        );
        service.updateDrug(drug);
    }

    private void recordVisit(Map<String, String> form) throws SQLException {
        Visit visit = new Visit(
                form.get("patientId"),
                form.get("doctorId"),
                parseDate(form.get("dateOfVisit"), "dateOfVisit"),
                emptyToNull(form.get("symptoms")),
                emptyToNull(form.get("diagnosisId"))
        );
        service.recordVisit(visit);
    }

    private void updateVisit(Map<String, String> form) throws SQLException {
        Visit visit = new Visit(
                form.get("patientId"),
                form.get("doctorId"),
                parseDate(form.get("dateOfVisit"), "dateOfVisit"),
                emptyToNull(form.get("symptoms")),
                emptyToNull(form.get("diagnosisId"))
        );
        service.updateVisit(
                form.get("originalPatientId"),
                form.get("originalDoctorId"),
                parseDate(form.get("originalDateOfVisit"), "originalDateOfVisit"),
                visit
        );
    }

    private void recordPrescription(Map<String, String> form) throws SQLException {
        Prescription prescription = new Prescription(
                form.get("prescriptionId"),
                parseDate(form.get("datePrescribed"), "datePrescribed"),
                form.get("dosage"),
                form.get("duration"),
                emptyToNull(form.get("comment")),
                form.get("drugId"),
                form.get("doctorId"),
                form.get("patientId")
        );
        service.recordPrescription(prescription);
    }

    private void updatePrescription(Map<String, String> form) throws SQLException {
        Prescription prescription = new Prescription(
                form.get("prescriptionId"),
                parseDate(form.get("datePrescribed"), "datePrescribed"),
                form.get("dosage"),
                form.get("duration"),
                emptyToNull(form.get("comment")),
                form.get("drugId"),
                form.get("doctorId"),
                form.get("patientId")
        );
        service.updatePrescription(prescription);
    }

    private void createClaim(Map<String, String> form) throws SQLException {
        service.createClaim(form.get("claimId"), form.get("prescriptionId"), form.get("notes"));
    }

    private void submitClaim(Map<String, String> form) throws SQLException {
        service.submitClaim(form.get("claimId"));
    }

    private void reviewClaim(Map<String, String> form) throws SQLException {
        service.markClaimUnderReview(form.get("claimId"), form.get("reviewedBy"));
    }

    private void approveClaim(Map<String, String> form) throws SQLException {
        service.approveClaim(form.get("claimId"), form.get("reviewedBy"), form.get("decisionNotes"));
    }

    private void rejectClaim(Map<String, String> form) throws SQLException {
        service.rejectClaim(form.get("claimId"), form.get("reviewedBy"), form.get("decisionNotes"));
    }

    private void saveViewSearch(Map<String, String> form) {
        savedViewSearchStore.save(new SavedViewSearch(
                form.get("savedSearchName"),
                defaultString(form.get("dataset"), ViewDataset.PATIENTS.key()),
                defaultString(form.get("query"), ""),
                defaultString(form.get("sort"), ""),
                defaultString(form.get("group"), ""),
                "on".equalsIgnoreCase(form.get("groupSort")) || "true".equalsIgnoreCase(form.get("groupSort")),
                safePositiveInt(form.get("pageSize"), 25)
        ));
    }

    private void deleteViewSearch(Map<String, String> form) {
        savedViewSearchStore.delete(form.get("name"));
    }

    private void updateViewRecord(Map<String, String> form) throws SQLException {
        ViewDataset dataset = ViewDataset.fromKey(form.get("dataset"));
        switch (dataset) {
            case PATIENTS -> updatePatient(form);
            case DOCTORS -> updateDoctor(form);
            case DRUGS -> updateDrug(form);
            case VISITS -> updateVisit(form);
            case PRESCRIPTIONS -> updatePrescription(form);
            case CLAIMS, CLAIM_REVIEW -> throw new ValidationException("Inline editing is not available for " + dataset.label().toLowerCase() + ".");
        }
    }

    private void deleteEntity(Map<String, String> form) throws SQLException {
        if (!"DELETE".equals(form.get("confirmation"))) {
            throw new ValidationException("Deletion requires confirmation text DELETE.");
        }
        String entityType = form.getOrDefault("entityType", "");
        switch (entityType) {
            case "insurance" -> service.deleteInsurance(form.get("id"));
            case "patient" -> service.deletePatient(form.get("id"));
            case "doctor" -> service.deleteDoctor(form.get("id"));
            case "drug" -> service.deleteDrug(form.get("id"));
            case "prescription" -> service.deletePrescription(form.get("id"));
            case "claim" -> service.deleteClaim(form.get("id"));
            case "visit" -> service.deleteVisit(
                    form.get("visitPatientId"),
                    form.get("visitDoctorId"),
                    parseDate(form.get("visitDate"), "visitDate")
            );
            default -> throw new ValidationException("Unknown entity type for delete.");
        }
    }

    private String renderPatientHistoryResults(String patientId) throws SQLException {
        if (patientId == null) {
            return tableNode("Patient history", "<p class=\"empty-state\">Enter a patient ID to inspect history.</p>");
        }
        Optional<PatientHistoryReport> report = service.getPatientHistoryReport(patientId);
        if (report.isEmpty()) {
            return tableNode("Patient history", "<p class=\"empty-state\">No patient found for ID " + escapeHtml(patientId) + ".</p>");
        }

        PatientHistoryReport history = report.get();
        String summary = "<div class=\"result-grid\">"
                + resultMetric("Patient", escapeHtml(history.patient().getPatientId()))
                + resultMetric("Name", escapeHtml(history.patient().getFirstName() + " " + history.patient().getSurname()))
                + resultMetric("Email", safeText(history.patient().getEmail()))
                + resultMetric("Phone", safeText(history.patient().getPhone()))
                + resultMetric("Insurance", history.insurance() == null ? safeText(history.patient().getInsuranceId()) : escapeHtml(history.insurance().getCompany()))
                + resultMetric("Primary doctor", history.primaryCareDoctor() == null
                        ? safeText(history.patient().getPrimaryCareDoctorId())
                        : escapeHtml(history.primaryCareDoctor().getFirstName() + " " + history.primaryCareDoctor().getSurname()))
                + "</div>";

        String visitsTable = history.visits().isEmpty()
                ? "<p class=\"empty-state\">No visits on file.</p>"
                : table(
                List.of("Date", "Doctor", "Symptoms", "Diagnosis"),
                history.visits().stream().map(visit -> List.of(
                        safeText(formatDate(visit.getDateOfVisit())),
                        safeText(visit.getDoctorId()),
                        safeText(visit.getSymptoms()),
                        safeText(visit.getDiagnosisId())
                )).toList());

        String prescriptionsTable = history.prescriptions().isEmpty()
                ? "<p class=\"empty-state\">No prescriptions on file.</p>"
                : table(
                List.of("Prescription", "Drug", "Prescriber", "Start", "End", "Active", "Eligible", "Issues"),
                history.prescriptions().stream().map(review -> List.of(
                        safeText(review.prescription().getPrescriptionId()),
                        safeText(review.drug() == null ? review.prescription().getDrugId() : review.drug().getDrugName()),
                        safeText(review.doctor() == null ? review.prescription().getDoctorId() : review.doctor().getFirstName() + " " + review.doctor().getSurname()),
                        safeText(formatDate(review.prescription().getDatePrescribed())),
                        safeText(formatDate(review.endDate())),
                        review.active() ? "<span class=\"pill pill-ok\">Active</span>" : "<span class=\"pill\">Expired</span>",
                        review.eligible() ? "<span class=\"pill pill-ok\">Eligible</span>" : "<span class=\"pill pill-warn\">Needs review</span>",
                        safeText(joinIssues(review.eligibilityIssues()))
                )).toList());

        return tableNode("Patient history", summary
                + "<div class=\"split-results\">"
                + "<section><h3>Visits</h3>" + visitsTable + "</section>"
                + "<section><h3>Prescriptions</h3>" + prescriptionsTable + "</section>"
                + "</div>");
    }

    private String renderPrescriptionReviewResults(String patientId) throws SQLException {
        List<PrescriptionReview> reviews = patientId == null
                ? service.getPrescriptionReviews()
                : service.getPrescriptionReviewsForPatient(patientId);

        if (reviews.isEmpty()) {
            return tableNode("Prescription review", "<p class=\"empty-state\">No prescriptions found for the current filter.</p>");
        }

        return tableNode("Prescription review", table(
                List.of("Prescription", "Patient", "Drug", "Prescriber", "Start", "End", "Active", "Eligible", "Dosage", "Issues"),
                reviews.stream().map(review -> List.of(
                        safeText(review.prescription().getPrescriptionId()),
                        safeText(review.prescription().getPatientId()),
                        safeText(review.drug() == null ? review.prescription().getDrugId() : review.drug().getDrugName()),
                        safeText(review.doctor() == null ? review.prescription().getDoctorId() : review.doctor().getFirstName() + " " + review.doctor().getSurname()),
                        safeText(formatDate(review.prescription().getDatePrescribed())),
                        safeText(formatDate(review.endDate())),
                        review.active() ? "<span class=\"pill pill-ok\">Active</span>" : "<span class=\"pill\">Expired</span>",
                        review.eligible() ? "<span class=\"pill pill-ok\">Eligible</span>" : "<span class=\"pill pill-warn\">Needs review</span>",
                        safeText(review.prescription().getDosage()),
                        safeText(joinIssues(review.eligibilityIssues()))
                )).toList()));
    }

    private String renderPatientSearchResults(String surname) throws SQLException {
        if (surname == null) {
            return tableNode("Patient search", "<p class=\"empty-state\">Enter a surname fragment to search.</p>");
        }
        List<Patient> patients = service.findPatientsBySurname(surname);
        if (patients.isEmpty()) {
            return tableNode("Patient search", "<p class=\"empty-state\">No patients matched " + escapeHtml(surname) + ".</p>");
        }
        return tableNode("Patient search", table(
                List.of("Patient", "Name", "Insurance", "Primary doctor", "Email"),
                patients.stream().map(patient -> List.of(
                        safeText(patient.getPatientId()),
                        safeText(patient.getFirstName() + " " + patient.getSurname()),
                        safeText(patient.getInsuranceId()),
                        safeText(patient.getPrimaryCareDoctorId()),
                        safeText(patient.getEmail())
                )).toList()));
    }

    private String renderDoctorSearchResults(String specialization) throws SQLException {
        if (specialization == null) {
            return tableNode("Doctor search", "<p class=\"empty-state\">Enter a specialization fragment to search.</p>");
        }
        List<Doctor> doctors = service.findDoctorsBySpecialization(specialization);
        if (doctors.isEmpty()) {
            return tableNode("Doctor search", "<p class=\"empty-state\">No doctors matched " + escapeHtml(specialization) + ".</p>");
        }
        return tableNode("Doctor search", table(
                List.of("Doctor", "Name", "Specialization", "Hospital", "Email"),
                doctors.stream().map(doctor -> List.of(
                        safeText(doctor.getDoctorId()),
                        safeText(doctor.getFirstName() + " " + doctor.getSurname()),
                        safeText(doctor.getSpecialization()),
                        safeText(doctor.getHospital()),
                        safeText(doctor.getEmail())
                )).toList()));
    }

    private String renderDrugSearchResults(String drugName) throws SQLException {
        if (drugName == null) {
            return tableNode("Drug search", "<p class=\"empty-state\">Enter a drug-name fragment to search.</p>");
        }
        List<Drug> drugs = service.findDrugsByName(drugName);
        if (drugs.isEmpty()) {
            return tableNode("Drug search", "<p class=\"empty-state\">No drugs matched " + escapeHtml(drugName) + ".</p>");
        }
        return tableNode("Drug search", table(
                List.of("Drug", "Name", "Purpose", "Side effects"),
                drugs.stream().map(drug -> List.of(
                        safeText(drug.getDrugId()),
                        safeText(drug.getDrugName()),
                        safeText(drug.getPurpose()),
                        safeText(drug.getSideEffects())
                )).toList()));
    }

    private String renderPage(String title, String subtitle, String status, Map<String, String> query, String body) {
        String path = currentPath(query);
        return """
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root {
                      --border-bold: 3px solid #111;
                      --border-med: 2px solid #111;
                      --border-thin: 1px solid #cfcfcf;
                      --bg-accent: #f4f2ee;
                      --bg-panel: #fbfaf8;
                      --bg-alert: #fff0d8;
                      --bg-danger: #f8d9d6;
                      --text-main: #101010;
                      --text-muted: #626262;
                      --success: #234c2f;
                      --warning: #7a3f00;
                      --danger: #8b1e1e;
                      --font-main: "Segoe UI", Tahoma, Helvetica, sans-serif;
                      --font-mono: "Courier New", Courier, monospace;
                    }
                    * { box-sizing: border-box; }
                    body { margin: 0; font-family: var(--font-main); font-size: 14px; line-height: 1.45; color: var(--text-main); background: linear-gradient(180deg, #ffffff 0%%, #f3f1ec 100%%); }
                    a { color: inherit; }
                    code { font-family: var(--font-mono); background: #ece9e3; padding: 1px 4px; }
                    #masthead { border-bottom: var(--border-bold); padding: 16px 18px; display: grid; grid-template-columns: 1fr auto; gap: 20px; background: #fff; }
                    #masthead h1 { margin: 0; font-size: 18px; text-transform: uppercase; letter-spacing: 0.08em; }
                    .subtitle { margin-top: 5px; color: var(--text-muted); font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; }
                    .metadata { border-left: var(--border-med); padding-left: 16px; font-family: var(--font-mono); font-size: 11px; }
                    .metadata div span { display: inline-block; width: 88px; color: var(--text-muted); }
                    #container { display: grid; grid-template-columns: 230px 1fr; min-height: calc(100vh - 76px); }
                    aside { border-right: var(--border-med); padding: 14px 12px; background: rgba(255,255,255,0.92); }
                    aside h2 { margin: 0 0 10px; font-size: 11px; text-transform: uppercase; border-bottom: 1px solid #111; padding-bottom: 6px; }
                    .index-link { display: block; margin-bottom: 6px; padding: 7px 9px; font-size: 12px; text-decoration: none; border-left: 3px solid transparent; }
                    .index-link:hover { background: var(--bg-accent); }
                    .index-link.hot { border-left-color: #111; background: var(--bg-accent); font-weight: 700; }
                    .const-block { margin-top: 18px; padding: 8px; border: var(--border-thin); font-size: 10px; color: var(--text-muted); line-height: 1.8; font-family: var(--font-mono); background: #fff; }
                    main { padding: 20px; }
                    .priority-banner { background: #111; color: #fff; padding: 9px 11px; font-size: 11px; margin-bottom: 20px; display: flex; justify-content: space-between; gap: 12px; text-transform: uppercase; letter-spacing: 0.06em; }
                    .priority-banner span:last-child { border: 1px solid #fff; padding: 0 6px; }
                    .flash { padding: 12px 14px; border: var(--border-med); margin-bottom: 18px; font-weight: 600; }
                    .flash-success { background: #e4f0e7; color: var(--success); }
                    .flash-error { background: #f5dada; color: var(--danger); }
                    .branch-section { border: var(--border-med); margin-bottom: 24px; background: rgba(255,255,255,0.95); }
                    .branch-header { background: #111; color: #fff; padding: 9px 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; }
                    .branch-body { padding: 12px; }
                    .dashboard-grid, .card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; }
                    .metric-card, .node { border: 1px solid #111; background: var(--bg-panel); }
                    .metric-card { padding: 14px; min-height: 126px; display: flex; flex-direction: column; justify-content: space-between; }
                    .metric-card .meta-label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; }
                    .metric-card .meta-value { font-size: 34px; font-weight: 700; line-height: 1; margin: 10px 0; }
                    .metric-card p { margin: 0; color: var(--text-muted); }
                    .node { margin-bottom: 12px; }
                    .node-header { display: flex; justify-content: space-between; gap: 12px; padding: 8px 10px; border-bottom: 1px solid #111; background: var(--bg-accent); }
                    .node-title { font-weight: 700; text-transform: uppercase; }
                    .node-body { padding: 12px; }
                    .node-body p { margin-top: 0; }
                    .node-actions { margin-top: 12px; }
                    .action-link { display: inline-block; padding: 8px 10px; border: 1px solid #111; text-decoration: none; font-weight: 700; background: #fff; }
                    .action-link:hover { background: var(--bg-accent); }
                    .form-card { border: 1px solid #111; background: var(--bg-panel); padding: 12px; }
                    .form-card h3 { margin: 0 0 10px; text-transform: uppercase; font-size: 13px; }
                    label { display: block; margin-top: 10px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
                    input, textarea, select, button { width: 100%%; margin-top: 4px; padding: 10px; border: 1px solid #111; border-radius: 0; font: inherit; background: #fff; color: var(--text-main); }
                    textarea { min-height: 92px; resize: vertical; }
                    button { background: #111; color: #fff; font-weight: 700; text-transform: uppercase; cursor: pointer; }
                    button:hover { background: #333; }
                    .advisory { margin: 0 0 6px; padding: 10px; background: var(--bg-alert); border-left: 3px solid #111; }
                    .checkbox-field { display: flex; align-items: center; gap: 8px; margin-top: 12px; font-size: 12px; text-transform: none; letter-spacing: 0; color: var(--text-main); }
                    .checkbox-field input { width: auto; margin: 0; }
                    table { width: 100%%; border-collapse: collapse; background: #fff; }
                    th, td { border-bottom: 1px solid #d7d7d7; padding: 10px 8px; text-align: left; vertical-align: top; }
                    th { font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; background: var(--bg-accent); }
                    .pill { display: inline-block; border: 1px solid #111; padding: 2px 7px; font-size: 10px; text-transform: uppercase; letter-spacing: 0.05em; }
                    .pill-ok { background: #d8eadb; color: var(--success); }
                    .pill-warn { background: #f5e0c9; color: var(--warning); }
                    .empty-state { margin: 0; padding: 12px; border: 1px dashed #999; color: var(--text-muted); background: #fff; }
                    .result-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
                    .result-metric { border: 1px solid #111; background: #fff; padding: 10px; }
                    .result-metric .label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; margin-bottom: 6px; }
                    .result-metric .value { font-weight: 700; }
                    .split-results { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
                    .split-results h3 { margin-top: 0; text-transform: uppercase; font-size: 12px; }
                    .group-block { border: 1px solid #111; background: #fff; margin-bottom: 14px; }
                    .group-header { display: flex; justify-content: space-between; gap: 12px; padding: 10px; border-bottom: 1px solid #111; background: var(--bg-accent); }
                    .inline-edit-cell { min-width: 280px; }
                    .inline-edit summary { cursor: pointer; font-weight: 700; text-transform: uppercase; font-size: 11px; }
                    .inline-edit form { margin-top: 10px; padding-top: 10px; border-top: 1px solid #d7d7d7; }
                    .inline-edit label { margin-top: 8px; }
                    @media (max-width: 980px) {
                      #container { grid-template-columns: 1fr; }
                      aside { border-right: 0; border-bottom: var(--border-med); }
                    }
                    @media (max-width: 720px) {
                      #masthead { grid-template-columns: 1fr; }
                      main { padding: 14px; }
                      .split-results { grid-template-columns: 1fr; }
                      .priority-banner { flex-direction: column; }
                    }
                  </style>
                </head>
                <body>
                  <header id="masthead">
                    <div>
                      <h1>Hospital Claims Command Surface</h1>
                      <div class="subtitle">%s</div>
                    </div>
                    <div class="metadata">
                      <div><span>Reference:</span> <b>HC-OPS.2026</b></div>
                      <div><span>Status:</span> <b>%s</b></div>
                    </div>
                  </header>
                  <div id="container">
                    <aside>
                      <h2>Index</h2>
                      %s
                      <div class="const-block">
                        Workflow separation enabled.<br>
                        Validation remains server-side.<br>
                        Destructive actions isolated.
                      </div>
                    </aside>
                    <main>%s</main>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(subtitle),
                escapeHtml(status),
                nav(path),
                body
        );
    }

    private String currentPath(Map<String, String> query) {
        return query.getOrDefault("__path", "/");
    }

    private String nav(String path) {
        return navLink("/", "Omega 1 · Dashboard", path)
                + navLink("/records", "Omega 2 · Records", path)
                + navLink("/claims", "Omega 3 · Claims", path)
                + navLink("/reviews", "Omega 4 · Investigations", path)
                + navLink("/view", "Omega 5 · View", path)
                + navLink("/delete", "Omega 6 · Delete", path);
    }

    private String navLink(String href, String label, String currentPath) {
        String classes = href.equals(currentPath) ? "index-link hot" : "index-link";
        return "<a class=\"" + classes + "\" href=\"" + href + "\">" + escapeHtml(label) + "</a>";
    }

    private String priorityBanner(String left, String right) {
        return "<div class=\"priority-banner\"><span>" + escapeHtml(left) + "</span><span>" + escapeHtml(right) + "</span></div>";
    }

    private String workflowSection(String title, String body) {
        return "<section class=\"branch-section\"><div class=\"branch-header\">" + escapeHtml(title)
                + "</div><div class=\"branch-body\">" + body + "</div></section>";
    }

    private String statCard(String label, String value, String detail) {
        return "<article class=\"metric-card\">"
                + "<div class=\"meta-label\">" + escapeHtml(label) + "</div>"
                + "<div class=\"meta-value\">" + escapeHtml(value) + "</div>"
                + "<p>" + escapeHtml(detail) + "</p>"
                + "</article>";
    }

    private String node(String title, String description, String body) {
        return "<article class=\"node\">"
                + "<div class=\"node-header\"><div class=\"node-title\">" + escapeHtml(title) + "</div></div>"
                + "<div class=\"node-body\"><p>" + escapeHtml(description) + "</p><div class=\"node-actions\">" + body + "</div></div>"
                + "</article>";
    }

    private String actionLink(String href, String label) {
        return "<a class=\"action-link\" href=\"" + href + "\">" + escapeHtml(label) + "</a>";
    }

    private String cardGrid(String... cards) {
        return "<div class=\"card-grid\">" + String.join("", cards) + "</div>";
    }

    private String formCard(String title, String action, String... contents) {
        return "<article class=\"form-card\"><h3>" + escapeHtml(title) + "</h3><form method=\"post\" action=\""
                + action + "\">" + String.join("", contents) + "</form></article>";
    }

    private String getFormCard(String title, String action, String... contents) {
        return "<article class=\"form-card\"><h3>" + escapeHtml(title) + "</h3><form method=\"get\" action=\""
                + action + "\">" + String.join("", contents) + "</form></article>";
    }

    private String tableNode(String title, String body) {
        return node(title, "Current dataset output", body);
    }

    private String submitButton(String label) {
        return "<button type=\"submit\">" + escapeHtml(label) + "</button>";
    }

    private String textField(String name, String label, boolean required) {
        return textFieldWithValue(name, label, null, required);
    }

    private String textFieldWithValue(String name, String label, String value, boolean required) {
        return "<label>" + escapeHtml(label)
                + "<input name=\"" + escapeHtml(name) + "\""
                + (value == null ? "" : " value=\"" + escapeHtml(value) + "\"")
                + (required ? " required" : "")
                + "></label>";
    }

    private String dateFieldWithValue(String name, String label, String value, boolean required) {
        return "<label>" + escapeHtml(label)
                + "<input type=\"date\" name=\"" + escapeHtml(name) + "\""
                + (value == null ? "" : " value=\"" + escapeHtml(value) + "\"")
                + (required ? " required" : "")
                + "></label>";
    }

    private String checkboxField(String name, String label, boolean checked) {
        return "<label class=\"checkbox-field\"><input type=\"checkbox\" name=\"" + escapeHtml(name) + "\""
                + (checked ? " checked" : "")
                + ">" + escapeHtml(label) + "</label>";
    }

    private String hiddenField(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escapeHtml(name) + "\" value=\"" + escapeHtml(value) + "\">";
    }

    private String dateField(String name, String label, boolean required) {
        return "<label>" + escapeHtml(label)
                + "<input type=\"date\" name=\"" + escapeHtml(name) + "\""
                + (required ? " required" : "")
                + "></label>";
    }

    private String textArea(String name, String label) {
        return "<label>" + escapeHtml(label) + "<textarea name=\"" + escapeHtml(name) + "\"></textarea></label>";
    }

    private String textAreaWithValue(String name, String label, String value) {
        return "<label>" + escapeHtml(label) + "<textarea name=\"" + escapeHtml(name) + "\">"
                + escapeHtml(defaultString(value, ""))
                + "</textarea></label>";
    }

    private String selectField(String name, String label, List<String> options) {
        return "<label>" + escapeHtml(label) + "<select name=\"" + escapeHtml(name) + "\">"
                + String.join("", options) + "</select></label>";
    }

    private String selectFieldWithValue(String name, String label, List<String> options, String selectedValue) {
        String selected = selectedValue == null ? "" : selectedValue;
        List<String> rewritten = new java.util.ArrayList<>();
        for (String option : options) {
            String marker = "value=\"" + escapeHtml(selected) + "\"";
            rewritten.add(option.contains(marker) ? option.replaceFirst("<option", "<option selected") : option);
        }
        return "<label>" + escapeHtml(label) + "<select name=\"" + escapeHtml(name) + "\">"
                + String.join("", rewritten) + "</select></label>";
    }

    private String option(String value, String label) {
        return "<option value=\"" + escapeHtml(value) + "\">" + escapeHtml(label) + "</option>";
    }

    private String table(List<String> headers, List<List<String>> rows) {
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr>");
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(cell).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String resultMetric(String label, String value) {
        return "<div class=\"result-metric\"><div class=\"label\">" + escapeHtml(label)
                + "</div><div class=\"value\">" + value + "</div></div>";
    }

    private String renderViewQueryCard(ViewSearchResult result) {
        return renderViewQueryCard(
                result.query().dataset(),
                result.query().rawQuery(),
                result.query().rawSort(),
                result.query().rawGroup(),
                result.query().groupSort(),
                String.valueOf(result.page()),
                String.valueOf(result.pageSize())
        );
    }

    private String renderViewQueryCard(ViewDataset dataset,
                                       String rawQuery,
                                       String rawSort,
                                       String rawGroup,
                                       boolean groupSort,
                                       String rawPage,
                                       String rawPageSize) {
        List<String> options = viewSearchService.datasets().stream()
                .map(item -> option(item.key(), item.label()))
                .toList();
        List<String> pageSizeOptions = new ArrayList<>();
        for (int pageSize : VIEW_PAGE_SIZES) {
            pageSizeOptions.add(option(String.valueOf(pageSize), String.valueOf(pageSize)));
        }
        return getFormCard("Run search", "/view",
                selectFieldWithValue("dataset", "Dataset", options, dataset.key()),
                textFieldWithValue("query", "Query", rawQuery, false),
                textFieldWithValue("sort", "Sort", rawSort, false),
                textFieldWithValue("group", "Group", rawGroup, false),
                selectFieldWithValue("pageSize", "Page size", pageSizeOptions, rawPageSize == null ? "25" : rawPageSize),
                checkboxField("groupSort", "Apply sort within groups", groupSort),
                submitButton("Search"),
                "<p class=\"advisory\">Examples: <code>surname:sm*</code> <code>!eligible status:approved</code> <code>email:re:^.+@example\\\\.com$</code></p>"
        );
    }

    private String renderViewFieldCatalog(ViewSearchResult result) {
        return renderViewFieldCatalog(result.query().dataset());
    }

    private String renderViewFieldCatalog(ViewDataset dataset) {
        ViewSearchService.ViewDatasetDefinition definition = viewSearchService.definitionFor(dataset);
        String columnsTable = table(
                List.of("Column", "Label", "Composed search"),
                definition.columns().stream()
                        .map(column -> List.of(
                                safeText(column.key()),
                                safeText(column.label()),
                                column.composable() ? "Key link" : ""
                        ))
                        .toList()
        );
        String flags = definition.flags().isEmpty()
                ? "<p class=\"empty-state\">No boolean flags are available for this dataset.</p>"
                : table(
                List.of("Flag"),
                definition.flags().stream().map(flag -> List.of(safeText(flag))).toList()
        );
        return node("Field catalog",
                "Available columns and flags for the selected dataset.",
                "<div class=\"split-results\">"
                        + "<section><h3>Columns</h3>" + columnsTable + "</section>"
                        + "<section><h3>Flags</h3>" + flags + "</section>"
                        + "</div>");
    }

    private String renderViewResults(ViewSearchResult result) throws SQLException {
        String summary = "<div class=\"result-grid\">"
                + resultMetric("Dataset", safeText(result.query().dataset().label()))
                + resultMetric("Records", safeText(String.valueOf(result.recordCount())))
                + resultMetric("Page", safeText(result.page() + " / " + result.totalPages()))
                + resultMetric("Grouping", safeText(result.query().groupColumns().isEmpty()
                ? "None"
                : String.join(", ", result.query().groupColumns())))
                + "</div>";
        String tools = renderViewTools(result);
        if (result.recordCount() == 0) {
            return summary + tools + "<p class=\"empty-state\">No records matched the current search.</p>";
        }
        if (!result.grouped()) {
            return summary + tools + renderViewRecordTable(result, result.records()) + renderPagination(result);
        }

        StringBuilder html = new StringBuilder(summary).append(tools);
        for (ViewGroup group : result.groups()) {
            String groupSummary = group.groupedValues().entrySet().stream()
                    .map(entry -> escapeHtml(entry.getKey()) + ": " + safeText(entry.getValue()))
                    .collect(Collectors.joining(" · "));
            html.append("<article class=\"group-block\">")
                    .append("<div class=\"group-header\"><strong>")
                    .append(groupSummary)
                    .append("</strong><span>")
                    .append(group.records().size())
                    .append(" records</span></div>")
                    .append(renderViewRecordTable(result, group.records()))
                    .append("</article>");
        }
        return html.append(renderPagination(result)).toString();
    }

    private String renderComposedViewResults(ViewComposedSearchResult result) throws SQLException {
        String summary = "<div class=\"result-grid\">"
                + resultMetric("Source dataset", safeText(result.query().sourceDataset().label()))
                + resultMetric("Source column", safeText(result.query().sourceColumnKey()))
                + resultMetric("Value", safeText(result.query().value()))
                + resultMetric("Datasets", safeText(String.valueOf(result.sections().size())))
                + resultMetric("Records", safeText(String.valueOf(result.totalRecords())))
                + "</div>";
        StringBuilder html = new StringBuilder(summary);
        for (ViewSearchResult section : result.sections()) {
            html.append("<article class=\"group-block\">")
                    .append("<div class=\"group-header\"><strong>")
                    .append(escapeHtml(section.query().dataset().label()))
                    .append("</strong><span>")
                    .append(section.recordCount())
                    .append(" records</span></div>");
            if (section.recordCount() == 0) {
                html.append("<p class=\"empty-state\">No records matched this dataset.</p>");
            } else {
                html.append(renderViewRecordTable(section, section.records()));
            }
            html.append("</article>");
        }
        return html.toString();
    }

    private String renderSavedSearches(ViewQuery query) {
        List<SavedViewSearch> savedSearches = savedViewSearchStore.findAll();
        String saveCard = formCard("Save current search", "/actions/save-view-search",
                hiddenField("dataset", query.dataset().key()),
                hiddenField("query", query.rawQuery()),
                hiddenField("sort", query.rawSort()),
                hiddenField("group", query.rawGroup()),
                hiddenField("groupSort", String.valueOf(query.groupSort())),
                hiddenField("pageSize", String.valueOf(query.pageSize())),
                textField("savedSearchName", "Search name", true),
                submitButton("Save search"));

        String listCard;
        if (savedSearches.isEmpty()) {
            listCard = node("Saved searches", "Reusable view queries stored on disk.",
                    "<p class=\"empty-state\">No saved searches yet.</p>");
        } else {
            StringBuilder body = new StringBuilder();
            for (SavedViewSearch search : savedSearches) {
                String href = buildViewLocation(Map.of(
                        "savedSearch", search.name(),
                        "page", "1"
                ));
                body.append("<article class=\"node\">")
                        .append("<div class=\"node-header\"><div class=\"node-title\">")
                        .append(escapeHtml(search.name()))
                        .append("</div></div>")
                        .append("<div class=\"node-body\"><p>")
                        .append(escapeHtml(search.dataset()))
                        .append(" · page size ")
                        .append(search.pageSize())
                        .append("</p><div class=\"node-actions\">")
                        .append(actionLink(href, "Run saved search"))
                        .append("</div><form method=\"post\" action=\"/actions/delete-view-search\">")
                        .append(hiddenField("name", search.name()))
                        .append(submitButton("Delete saved search"))
                        .append("</form></div></article>");
            }
            listCard = node("Saved searches", "Reusable view queries stored on disk.", body.toString());
        }
        return cardGrid(saveCard, listCard);
    }

    private String renderViewTools(ViewSearchResult result) {
        String csvHref = buildExportLocation(result.query(), "csv");
        String jsonHref = buildExportLocation(result.query(), "json");
        return "<div class=\"node-actions\" style=\"margin-bottom: 12px;\">"
                + actionLink(csvHref, "Export CSV")
                + " "
                + actionLink(jsonHref, "Export JSON")
                + "</div>";
    }

    private String renderPagination(ViewSearchResult result) {
        if (result.totalPages() <= 1) {
            return "";
        }
        StringBuilder html = new StringBuilder("<div class=\"node-actions\" style=\"margin-top: 12px;\">");
        if (result.hasPreviousPage()) {
            html.append(actionLink(buildViewLocation(result.query(), result.page() - 1), "Previous page"));
        }
        if (result.hasNextPage()) {
            if (result.hasPreviousPage()) {
                html.append(" ");
            }
            html.append(actionLink(buildViewLocation(result.query(), result.page() + 1), "Next page"));
        }
        html.append("</div>");
        return html.toString();
    }

    private String renderViewRecordTable(ViewSearchResult result, List<ViewRecord> records) throws SQLException {
        List<ViewColumn> columns = result.columns();
        boolean editable = isInlineEditable(result.query().dataset());
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr>");
        for (ViewColumn column : columns) {
            html.append("<th>").append(escapeHtml(column.label())).append("</th>");
        }
        if (editable) {
            html.append("<th>Edit</th>");
        }
        html.append("</tr></thead><tbody>");
        for (ViewRecord record : records) {
            html.append("<tr>");
            for (ViewColumn column : columns) {
                html.append("<td>").append(renderViewCell(result.query().dataset(), column, record)).append("</td>");
            }
            if (editable) {
                html.append("<td class=\"inline-edit-cell\">")
                        .append(renderInlineEditForm(result.query(), record))
                        .append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String renderViewCell(ViewDataset dataset, ViewColumn column, ViewRecord record) {
        String value = record.stringValue(column.key());
        if (!column.composable() || value.isBlank() || !viewSearchService.isComposableColumn(dataset, column.key())) {
            return safeText(value);
        }
        return "<a href=\"" + escapeHtml(buildComposedViewLocation(dataset, column.key(), value)) + "\">"
                + safeText(value)
                + "</a>";
    }

    private String renderViewCsv(ViewSearchResult result) {
        List<String> headers = new ArrayList<>(result.columns().stream().map(ViewColumn::key).toList());
        headers.addAll(result.flags());
        List<List<String>> rows = result.records().stream()
                .map(record -> {
                    List<String> values = new ArrayList<>();
                    for (ViewColumn column : result.columns()) {
                        values.add(record.stringValue(column.key()));
                    }
                    for (String flag : result.flags()) {
                        values.add(String.valueOf(record.flagValue(flag)));
                    }
                    return values;
                }).toList();

        StringBuilder csv = new StringBuilder();
        csv.append(headers.stream().map(this::csvCell).collect(Collectors.joining(","))).append("\n");
        for (List<String> row : rows) {
            csv.append(row.stream().map(this::csvCell).collect(Collectors.joining(","))).append("\n");
        }
        return csv.toString();
    }

    private String renderViewJson(ViewSearchResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"dataset\":\"").append(jsonEscape(result.query().dataset().key())).append("\",")
                .append("\"query\":").append(jsonString(result.query().rawQuery())).append(",")
                .append("\"sort\":").append(jsonString(result.query().rawSort())).append(",")
                .append("\"group\":").append(jsonString(result.query().rawGroup())).append(",")
                .append("\"groupSort\":").append(result.query().groupSort()).append(",")
                .append("\"totalRecords\":").append(result.recordCount()).append(",")
                .append("\"grouped\":").append(result.grouped()).append(",")
                .append("\"records\":[");
        List<String> recordJson = result.records().stream().map(record -> {
            List<String> pairs = new ArrayList<>();
            for (ViewColumn column : result.columns()) {
                pairs.add("\"" + jsonEscape(column.key()) + "\":" + jsonString(record.stringValue(column.key())));
            }
            for (String flag : result.flags()) {
                pairs.add("\"" + jsonEscape(flag) + "\":" + record.flagValue(flag));
            }
            return "{" + String.join(",", pairs) + "}";
        }).toList();
        json.append(String.join(",", recordJson)).append("]}");
        return json.toString();
    }

    private String renderFlash(Map<String, String> query) {
        if (query.containsKey("message")) {
            return "<div class=\"flash flash-success\">" + escapeHtml(query.get("message")) + "</div>";
        }
        if (query.containsKey("error")) {
            return "<div class=\"flash flash-error\">" + escapeHtml(query.get("error")) + "</div>";
        }
        return "";
    }

    private Map<String, String> applySavedSearch(Map<String, String> query) {
        Map<String, String> merged = new HashMap<>(query);
        String savedSearchName = trimToNull(query.get("savedSearch"));
        if (savedSearchName == null) {
            return merged;
        }
        SavedViewSearch savedSearch = savedViewSearchStore.findByName(savedSearchName);
        merged.put("dataset", savedSearch.dataset());
        merged.put("query", savedSearch.query());
        merged.put("sort", savedSearch.sort());
        merged.put("group", savedSearch.group());
        merged.put("groupSort", String.valueOf(savedSearch.groupSort()));
        merged.putIfAbsent("pageSize", String.valueOf(savedSearch.pageSize()));
        merged.putIfAbsent("page", "1");
        return merged;
    }

    private boolean isInlineEditable(ViewDataset dataset) {
        return switch (dataset) {
            case PATIENTS, DOCTORS, DRUGS, VISITS, PRESCRIPTIONS -> true;
            case CLAIMS, CLAIM_REVIEW -> false;
        };
    }

    private String renderInlineEditForm(ViewQuery query, ViewRecord record) throws SQLException {
        String formFields = switch (query.dataset()) {
            case PATIENTS -> renderPatientInlineFields(record);
            case DOCTORS -> renderDoctorInlineFields(record);
            case DRUGS -> renderDrugInlineFields(record);
            case VISITS -> renderVisitInlineFields(record);
            case PRESCRIPTIONS -> renderPrescriptionInlineFields(record);
            case CLAIMS, CLAIM_REVIEW -> "";
        };
        return "<details class=\"inline-edit\"><summary>Edit row</summary><form method=\"post\" action=\"/actions/view-inline-edit\">"
                + hiddenViewStateFields(query)
                + formFields
                + submitButton("Save row")
                + "</form></details>";
    }

    private String renderPatientInlineFields(ViewRecord record) throws SQLException {
        Patient patient = service.findPatientById(record.stringValue("patientId"))
                .orElseThrow(() -> new ValidationException("Patient " + record.stringValue("patientId") + " was not found."));
        return hiddenField("dataset", ViewDataset.PATIENTS.key())
                + textFieldWithValue("patientId", "Patient ID", patient.getPatientId(), true)
                + textFieldWithValue("firstName", "First name", patient.getFirstName(), true)
                + textFieldWithValue("surname", "Surname", patient.getSurname(), true)
                + textFieldWithValue("postcode", "Postcode", patient.getPostcode(), false)
                + textAreaWithValue("address", "Address", patient.getAddress())
                + textFieldWithValue("phone", "Phone", patient.getPhone(), false)
                + textFieldWithValue("email", "Email", patient.getEmail(), true)
                + textFieldWithValue("insuranceId", "Insurance ID", patient.getInsuranceId(), false)
                + textFieldWithValue("primaryCareDoctorId", "Primary care doctor ID", patient.getPrimaryCareDoctorId(), false);
    }

    private String renderDoctorInlineFields(ViewRecord record) throws SQLException {
        Doctor doctor = service.findDoctorById(record.stringValue("doctorId"))
                .orElseThrow(() -> new ValidationException("Doctor " + record.stringValue("doctorId") + " was not found."));
        return hiddenField("dataset", ViewDataset.DOCTORS.key())
                + textFieldWithValue("doctorId", "Doctor ID", doctor.getDoctorId(), true)
                + textFieldWithValue("firstName", "First name", doctor.getFirstName(), true)
                + textFieldWithValue("surname", "Surname", doctor.getSurname(), true)
                + textAreaWithValue("address", "Address", doctor.getAddress())
                + textFieldWithValue("phone", "Phone", doctor.getPhone(), false)
                + textFieldWithValue("email", "Email", doctor.getEmail(), true)
                + textFieldWithValue("specialization", "Specialization", doctor.getSpecialization(), false)
                + textFieldWithValue("hospital", "Hospital", doctor.getHospital(), false);
    }

    private String renderDrugInlineFields(ViewRecord record) throws SQLException {
        Drug drug = service.findDrugById(record.stringValue("drugId"))
                .orElseThrow(() -> new ValidationException("Drug " + record.stringValue("drugId") + " was not found."));
        return hiddenField("dataset", ViewDataset.DRUGS.key())
                + textFieldWithValue("drugId", "Drug ID", drug.getDrugId(), true)
                + textFieldWithValue("drugName", "Drug name", drug.getDrugName(), true)
                + textAreaWithValue("sideEffects", "Side effects", drug.getSideEffects())
                + textAreaWithValue("purpose", "Purpose", drug.getPurpose());
    }

    private String renderVisitInlineFields(ViewRecord record) {
        String patientId = record.stringValue("patientId");
        String doctorId = record.stringValue("doctorId");
        String dateOfVisit = record.stringValue("dateOfVisit");
        return hiddenField("dataset", ViewDataset.VISITS.key())
                + hiddenField("originalPatientId", patientId)
                + hiddenField("originalDoctorId", doctorId)
                + hiddenField("originalDateOfVisit", dateOfVisit)
                + textFieldWithValue("patientId", "Patient ID", patientId, true)
                + textFieldWithValue("doctorId", "Doctor ID", doctorId, true)
                + dateFieldWithValue("dateOfVisit", "Visit date", dateOfVisit, true)
                + textAreaWithValue("symptoms", "Symptoms", record.stringValue("symptoms"))
                + textFieldWithValue("diagnosisId", "Diagnosis ID", record.stringValue("diagnosisId"), false);
    }

    private String renderPrescriptionInlineFields(ViewRecord record) throws SQLException {
        Prescription prescription = service.findPrescriptionById(record.stringValue("prescriptionId"))
                .orElseThrow(() -> new ValidationException("Prescription " + record.stringValue("prescriptionId") + " was not found."));
        return hiddenField("dataset", ViewDataset.PRESCRIPTIONS.key())
                + textFieldWithValue("prescriptionId", "Prescription ID", prescription.getPrescriptionId(), true)
                + dateFieldWithValue("datePrescribed", "Date prescribed", formatDate(prescription.getDatePrescribed()), true)
                + textFieldWithValue("dosage", "Dosage", prescription.getDosage(), true)
                + textFieldWithValue("duration", "Duration (days)", prescription.getDuration(), true)
                + textAreaWithValue("comment", "Clinical comment", prescription.getComment())
                + textFieldWithValue("drugId", "Drug ID", prescription.getDrugId(), true)
                + textFieldWithValue("doctorId", "Doctor ID", prescription.getDoctorId(), true)
                + textFieldWithValue("patientId", "Patient ID", prescription.getPatientId(), true);
    }

    private String hiddenViewStateFields(ViewQuery query) {
        return hiddenField("query", query.rawQuery())
                + hiddenField("sort", query.rawSort())
                + hiddenField("group", query.rawGroup())
                + hiddenField("groupSort", String.valueOf(query.groupSort()))
                + hiddenField("pageSize", String.valueOf(query.pageSize()))
                + hiddenField("page", String.valueOf(query.page()));
    }

    private Map<String, String> viewStateFromForm(Map<String, String> form) {
        Map<String, String> state = new LinkedHashMap<>();
        state.put("dataset", defaultString(form.get("dataset"), ViewDataset.PATIENTS.key()));
        state.put("query", defaultString(form.get("query"), ""));
        state.put("sort", defaultString(form.get("sort"), ""));
        state.put("group", defaultString(form.get("group"), ""));
        state.put("groupSort", defaultString(form.get("groupSort"), "false"));
        state.put("pageSize", defaultString(form.get("pageSize"), "25"));
        state.put("page", defaultString(form.get("page"), "1"));
        return state;
    }

    private String buildViewLocation(ViewQuery query, int page) {
        return buildViewLocation(Map.of(
                "dataset", query.dataset().key(),
                "query", query.rawQuery(),
                "sort", query.rawSort(),
                "group", query.rawGroup(),
                "groupSort", String.valueOf(query.groupSort()),
                "pageSize", String.valueOf(query.pageSize()),
                "page", String.valueOf(page)
        ));
    }

    private String buildExportLocation(ViewQuery query, String format) {
        return buildViewLocation("/view/export", Map.of(
                "dataset", query.dataset().key(),
                "query", query.rawQuery(),
                "sort", query.rawSort(),
                "group", query.rawGroup(),
                "groupSort", String.valueOf(query.groupSort()),
                "format", format
        ));
    }

    private String buildComposedViewLocation(ViewDataset dataset, String sourceColumn, String value) {
        return buildViewLocation(Map.of(
                "composed", "on",
                "sourceDataset", dataset.key(),
                "sourceColumn", sourceColumn,
                "value", value
        ));
    }

    private String buildViewLocation(Map<String, String> params) {
        return buildViewLocation("/view", params);
    }

    private String buildViewLocation(Map<String, String> baseParams, Map<String, String> extraParams) {
        Map<String, String> merged = new LinkedHashMap<>(baseParams);
        merged.putAll(extraParams);
        return buildViewLocation("/view", merged);
    }

    private String buildViewLocation(String path, Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank() && !entry.getKey().startsWith("__"))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return queryString.isEmpty() ? path : path + "?" + queryString;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    /** Sends an HTML response body. */
    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    /** Sends a file download response. */
    private void sendDownload(HttpExchange exchange, String contentType, String fileName, String bodyText) throws IOException {
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    /** Rejects unsupported HTTP methods. */
    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    /** Renders a generic error page for unexpected request failures. */
    private void sendErrorPage(HttpExchange exchange, Exception exception) throws IOException {
        Map<String, String> query = new HashMap<>();
        query.put("__path", exchange.getRequestURI().getPath());
        String body = renderFlash(Map.of("error", exception.getMessage()))
                + workflowSection("Database error", "<p class=\"empty-state\">The requested view could not be rendered.</p>");
        sendHtml(exchange, 500, renderPage("Error", "Hospital claims operations console", "Database fault", query, body));
    }

    /** Parses URL-encoded query strings and form bodies into a simple map. */
    private Map<String, String> parseForm(String rawForm) {
        Map<String, String> values = new HashMap<>();
        if (rawForm == null || rawForm.isBlank()) {
            return values;
        }
        for (String pair : rawForm.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            // Split once so values may legitimately contain encoded '=' characters.
            String[] tokens = pair.split("=", 2);
            String key = urlDecode(tokens[0]);
            String value = tokens.length > 1 ? urlDecode(tokens[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    private String safeText(String value) {
        return escapeHtml(value == null || value.isBlank() ? "-" : value);
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private int safePositiveInt(String rawValue, int fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String statusPill(String value) {
        return "<span class=\"pill\">" + safeText(value) + "</span>";
    }

    private String joinIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "None";
        }
        StringJoiner joiner = new StringJoiner("; ");
        for (String issue : issues) {
            joiner.add(issue);
        }
        return joiner.toString();
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank.");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw new ValidationException(fieldName + " must use YYYY-MM-DD.");
        }
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + jsonEscape(value) + "\"";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        try {
            String html = Files.readString(Path.of("Login.html"), StandardCharsets.UTF_8);
            sendHtml(exchange, 200, html);
        } catch (IOException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    private void handleError(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        try {
            String html = Files.readString(Path.of("Error.html"), StandardCharsets.UTF_8);
            sendHtml(exchange, 200, html);
        } catch (IOException exception) {
            sendErrorPage(exchange, exception);
        }
    }

    private void handleLoginAction(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        try {
            Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String operatorId = form.get("operator-id");
            String passphrase = form.get("passphrase");
            
            // Call the placeholder authentication function that always passes
            boolean authenticated = authenticate(operatorId, passphrase);
            
            if (authenticated) {
                redirect(exchange, "/");
            } else {
                redirect(exchange, "/Login.html?error=" + urlEncode("Authentication failed"));
            }
        } catch (Exception exception) {
            redirect(exchange, "/Login.html?error=" + urlEncode(exception.getMessage()));
        }
    }

    /** Placeholder authentication function that always passes. */
    public boolean authenticate(String operatorId, String passphrase) {
        // Always passes as requested
        return true;
    }

    @FunctionalInterface
    private interface Action {
        void run(Map<String, String> form) throws SQLException;
    }
}
