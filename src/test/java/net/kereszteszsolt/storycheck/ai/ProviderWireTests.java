// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import net.kereszteszsolt.storycheck.review.ReviewOutputConverter;
import net.kereszteszsolt.storycheck.review.ReviewRequest;
import net.kereszteszsolt.storycheck.review.ReviewTestData;
import net.kereszteszsolt.storycheck.review.StoryReviewService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class ProviderWireTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ValidatorFactory validation;
    private HttpServer server;
    private ExecutorService executor;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private volatile int status = 200;
    private volatile long delayMs;
    private volatile boolean responseSent;
    private volatile String completionText = "fixed answer";

    @BeforeAll
    static void createValidator() {
        validation = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validation.close();
    }

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(path, exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("X-Stainless-Timeout"),
                    exchange.getRequestHeaders().getFirst("X-Stainless-Retry-Count")));
            try {
                Thread.sleep(delayMs);
                String body = status != 200 ? "{\"error\":{\"message\":\"private-key raw-story\"}}"
                        : path.startsWith("/ollama/") ? """
                        {"model":"second","created_at":"2026-10-17T00:00:00Z",
                         "message":{"role":"assistant","content":%s},"done":true,
                         "done_reason":"stop","prompt_eval_count":1,"eval_count":1}
                        """.formatted(JSON.writeValueAsString(completionText)) : """
                        {"id":"stub","object":"chat.completion","created":1,"model":"vendor/second",
                         "choices":[{"index":0,"message":{"role":"assistant","content":%s},
                         "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """.formatted(JSON.writeValueAsString(completionText));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                responseSent = true;
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            catch (IOException closedAfterTimeout) {
                // Timeout tests deliberately close the client before the stub responds.
            }
            finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
        executor.shutdownNow();
    }

    private ProviderChatService service() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var properties = new ProviderProperties(new ProviderProperties.Ollama(base + "/ollama", List.of("first", "second")),
                new ProviderProperties.OpenRouter(base + "/api/v1", "dummy-key", List.of("vendor/first", "vendor/second")), 1000);
        var configuration = new ProviderConfiguration();
        return configuration.providerChatService(properties, configuration.modelRegistry(properties));
    }

    private String model(String provider) {
        return provider.equals("ollama") ? "second" : "vendor/second";
    }

    private StoryReviewService reviews(ProviderChatService providers) {
        var validator = validation.getValidator();
        return new StoryReviewService(providers, new ReviewOutputConverter(validator), validator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openrouter"})
    void structuredReviewsUseTheSameNativeSchemaAndLiteralStoryOnBothAdapters(String provider) throws Exception {
        completionText = ReviewTestData.VALID_JSON;
        try (var providers = service()) {
            var review = reviews(providers).review(new ReviewRequest(provider, model(provider), ReviewTestData.STORY));
            assertEquals("The story is clear and testable.", review.summary());
            assertEquals(List.of(), review.clarificationQuestions());
            assertEquals(1, requests.size());
            var json = JSON.readTree(requests.peek().body());
            assertEquals(model(provider), json.get("model").asString());
            assertEquals("system", json.get("messages").get(0).get("role").asString());
            assertTrue(json.get("messages").get(0).get("content").asString().contains("fill a quota"));
            assertEquals(ReviewTestData.STORY, json.get("messages").get(1).get("content").asString());
            var schema = provider.equals("ollama") ? json.get("format")
                    : json.get("response_format").get("json_schema").get("schema");
            assertEquals(JSON.readTree(new ReviewOutputConverter(validation.getValidator()).getJsonSchema()), schema);
            assertEquals(4, schema.get("required").size());
            assertFalse(schema.get("additionalProperties").asBoolean());
            assertEquals(0, schema.get("properties").get("clarificationQuestions").get("minItems").asInt());
            if (provider.equals("openrouter")) {
                assertEquals("json_schema", json.get("response_format").get("type").asString());
                assertTrue(json.get("response_format").get("json_schema").get("strict").asBoolean());
                assertFalse(json.get("provider").get("allow_fallbacks").asBoolean());
                assertEquals("0", requests.peek().retries());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"ollama,invalid", "openrouter,invalid", "ollama,empty", "openrouter,empty",
            "ollama,unsupported", "openrouter,unsupported"})
    void invalidOrUnsupportedStructuredResultsFailWithoutRepairRetries(String provider, String scenario) throws Exception {
        completionText = scenario.equals("empty") ? "" : "{\"summary\":\"private-key raw-story\"}";
        status = scenario.equals("unsupported") ? 400 : 200;
        try (var providers = service()) {
            var failure = assertThrows(ProviderException.class,
                    () -> reviews(providers).review(new ReviewRequest(provider, model(provider), ReviewTestData.STORY)));
            assertEquals(scenario.equals("unsupported") ? ProviderException.Reason.REJECTED
                    : ProviderException.Reason.INVALID_RESPONSE, failure.reason());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private-key"));
            assertEquals(1, requests.size());
            assertEquals(provider.equals("ollama") ? "/ollama/api/chat" : "/api/v1/chat/completions",
                    requests.peek().path());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openrouter"})
    void realAdaptersUseSelectedModelExactPathAndBackendCredentials(String provider) throws Exception {
        try (var service = service()) {
            assertTrue(requests.isEmpty(), "Startup must not discover or download models");
            assertEquals("fixed answer", service.complete(provider, model(provider), "Literal {story}"));
            Request request = requests.poll(2, TimeUnit.SECONDS);
            assertNotNull(request);
            var json = JsonMapper.builder().build().readTree(request.body());
            assertEquals(model(provider), json.get("model").asString());
            assertEquals("Literal {story}", json.get("messages").get(0).get("content").asString());
            assertTrue(json.get("stream") == null || !json.get("stream").asBoolean());
            if (provider.equals("ollama")) {
                assertEquals("/ollama/api/chat", request.path());
                assertNull(request.authorization());
                assertEquals(1024, json.get("options").get("num_predict").asInt());
            }
            else {
                assertEquals("/api/v1/chat/completions", request.path());
                assertEquals("Bearer dummy-key", request.authorization());
                assertEquals("1", request.timeout());
                assertEquals("0", request.retries());
                assertEquals(1024, json.get("max_tokens").asInt());
                assertFalse(json.get("provider").get("allow_fallbacks").asBoolean());
                assertEquals("none", json.get("tool_choice").asString());
            }
            assertTrue(requests.isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"ollama,429,RATE_LIMITED", "ollama,500,UNAVAILABLE", "ollama,404,UNAVAILABLE",
            "openrouter,429,RATE_LIMITED", "openrouter,500,UNAVAILABLE", "openrouter,401,UNAVAILABLE",
            "openrouter,400,REJECTED", "openrouter,504,TIMEOUT"})
    void providerFailuresAreSafeAndNeverRetriedOrRerouted(String provider, int status, ProviderException.Reason reason)
            throws Exception {
        this.status = status;
        try (var service = service()) {
            var failure = assertThrows(ProviderException.class,
                    () -> service.complete(provider, model(provider), "fictional input"));
            assertEquals(reason, failure.reason());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private-key"));
            assertEquals(1, requests.size());
            assertEquals(provider.equals("ollama") ? "/ollama/api/chat" : "/api/v1/chat/completions",
                    requests.peek().path());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openrouter"})
    void configuredTimeoutIsAppliedToBothAdapters(String provider) throws Exception {
        try (var service = service()) {
            // Exclude first-call SDK class loading from the network timeout assertion.
            assertEquals("fixed answer", service.complete(provider, model(provider), "warm up fixture"));
            requests.clear();
            responseSent = false;
            delayMs = 2000;
            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
                var failure = assertThrows(ProviderException.class,
                        () -> service.complete(provider, model(provider), "fictional input"));
                assertEquals(ProviderException.Reason.TIMEOUT, failure.reason());
            }, () -> requests.stream().map(request -> request.path() + " timeout=" + request.timeout()
                    + " retries=" + request.retries()).toList() + " responseSent=" + responseSent);
            assertEquals(1, requests.size());
        }
    }

    private record Request(String path, String authorization, String body, String timeout, String retries) {}
}
