package com.hospitalclaims;

import com.hospitalclaims.service.HospitalClaimsService;
import com.hospitalclaims.service.SavedViewSearchStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalClaimsHttpServerTest {
    @Test
    void rendersGroupedViewWorkspaceResults() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=claims&group=status&sort=createdDate:desc&groupSort=on");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("View workspace"));
            assertTrue(response.body().contains("Field catalog"));
            assertTrue(response.body().contains("claimId"));
            assertTrue(response.body().contains("SUBMITTED"));
            assertTrue(response.body().contains("APPROVED"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersSearchValidationErrorsInline() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=patients&query=surname:re:*broken");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Invalid regex"));
            assertTrue(response.body().contains("Correct the query and run the search again."));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersClaimReviewDatasetInViewWorkspace() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=claim-review&query=eligible");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("claim-review"));
            assertTrue(response.body().contains("RX1"));
            assertTrue(response.body().contains("doctorName"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersPaginationControlsForViewWorkspace() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=patients&sort=patientId:asc&page=2&pageSize=1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Page"));
            assertTrue(response.body().contains("Previous page"));
            assertTrue(response.body().contains("PAT2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersInlineEditControlsForEditableViewDatasets() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=patients&sort=patientId:asc");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("/actions/view-inline-edit"));
            assertTrue(response.body().contains("Edit row"));
            assertTrue(response.body().contains("Primary care doctor ID"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersComposableValueLinksInViewResults() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?dataset=claims&query=claimId:=CLM1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("composed=on"));
            assertTrue(response.body().contains("sourceDataset=claims"));
            assertTrue(response.body().contains("sourceColumn=patientId"));
            assertTrue(response.body().contains("value=PAT1"));
            assertTrue(response.body().contains("sourceColumn=insuranceId"));
            assertTrue(response.body().contains("value=INS1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rendersComposedSearchResultsAcrossDatasets() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view?composed=on&sourceDataset=claims&sourceColumn=patientId&value=PAT1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Composed record retrieval"));
            assertTrue(response.body().contains("Source dataset"));
            assertTrue(response.body().contains("Patients"));
            assertTrue(response.body().contains("Claims"));
            assertTrue(response.body().contains("Prescriptions"));
            assertTrue(response.body().contains("claimId"));
            assertTrue(response.body().contains("RX1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void updatesPatientInlineAndReturnsToCurrentView() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> updateResponse = post(server,
                    "/actions/view-inline-edit",
                    "dataset=patients&query=&sort=patientId%3Aasc&group=&groupSort=false&pageSize=25&page=1"
                            + "&patientId=PAT2&firstName=Liam&surname=Stone&postcode=XY9+9ZZ&address=8+Side+St"
                            + "&phone=07000000000&email=liam%40example.com&insuranceId=&primaryCareDoctorId=");

            assertEquals(303, updateResponse.statusCode());
            String location = updateResponse.headers().firstValue("Location").orElseThrow();
            assertEquals("/view?dataset=patients&sort=patientId%3Aasc&groupSort=false&pageSize=25&page=1&message=Updated+patients+record.",
                    location);

            HttpResponse<String> viewResponse = fetch(server, location);

            assertEquals(200, viewResponse.statusCode());
            assertTrue(viewResponse.body().contains("07000000000"));
            assertTrue(viewResponse.body().contains("Updated patients record."));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void savesAndLoadsSavedSearches() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> saveResponse = post(server,
                    "/actions/save-view-search",
                    "savedSearchName=Claims+By+Status&dataset=claims&query=eligible&sort=status%3Aasc&group=status&groupSort=on&pageSize=10");

            assertEquals(303, saveResponse.statusCode());

            HttpResponse<String> viewResponse = fetch(server, "/view?savedSearch=Claims+By+Status");

            assertEquals(200, viewResponse.statusCode());
            assertTrue(viewResponse.body().contains("Claims By Status"));
            assertTrue(viewResponse.body().contains("eligible"));
            assertTrue(viewResponse.body().contains("status"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exportsCsvFromViewWorkspace() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view/export?format=csv&dataset=patients&sort=patientId:asc");

            assertEquals(200, response.statusCode());
            assertEquals("text/csv; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(response.body().contains("\"patientId\",\"firstName\""));
            assertTrue(response.body().contains("\"PAT1\",\"Ava\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exportsJsonFromViewWorkspace() throws Exception {
        HospitalClaimsService service = TestSupport.sampleService();
        HospitalClaimsHttpServer server = createServer(service);
        server.start();
        try {
            HttpResponse<String> response = fetch(server, "/view/export?format=json&dataset=claims&query=eligible");

            assertEquals(200, response.statusCode());
            assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(response.body().contains("\"dataset\":\"claims\""));
            assertTrue(response.body().contains("\"claimId\":\"CLM1\""));
        } finally {
            server.stop(0);
        }
    }

    private HospitalClaimsHttpServer createServer(HospitalClaimsService service) throws IOException {
        Path tempFile = Files.createTempFile("view-searches-", ".properties");
        return new HospitalClaimsHttpServer(service, 0, new SavedViewSearchStore(tempFile));
    }

    private HttpResponse<String> fetch(HospitalClaimsHttpServer server, String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.getPort() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HospitalClaimsHttpServer server, String path, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.getPort() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
