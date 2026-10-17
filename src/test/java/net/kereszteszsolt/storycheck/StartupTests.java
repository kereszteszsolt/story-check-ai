// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StartupTests {
    @LocalServerPort
    private int port;

    @Test
    void sameApplicationServesHtmlAndHealthOverHttp() throws Exception {
        HttpResponse<String> page = get("/", "text/html");
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(page.body().contains("<h1>StoryCheck AI</h1>"));

        HttpResponse<String> health = get("/api/health", "application/json");
        assertEquals(200, health.statusCode());
        assertEquals("{\"status\":\"ok\"}", health.body());
    }

    @Test
    void missingRoutesStayJsonEvenForBrowserRequests() throws Exception {
        HttpResponse<String> response = get("/api/missing", "text/html");
        assertEquals(404, response.statusCode());
        assertEquals("{\"code\":\"NOT_FOUND\",\"message\":\"The requested resource was not found.\"}",
                response.body());
    }

    @Test
    void errorFallbackIgnoresUserSuppliedDetails() throws Exception {
        HttpResponse<String> response = get("/error?message=secret&trace=true", "text/html");
        assertEquals(500, response.statusCode());
        assertEquals("{\"code\":\"INTERNAL_ERROR\",\"message\":\"The request could not be completed.\"}",
                response.body());
    }

    @Test
    void testFixturesAreAbsentFromProductionContext() throws Exception {
        assertEquals(404, get("/api/test/failure", "application/json").statusCode());
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).header("Accept", accept).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
