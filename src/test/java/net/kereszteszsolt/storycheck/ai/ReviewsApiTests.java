// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.kereszteszsolt.storycheck.review.ReviewRequest;
import net.kereszteszsolt.storycheck.review.ReviewTestData;
import net.kereszteszsolt.storycheck.review.StoryReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"OLLAMA_MODEL_1=local", "OLLAMA_BASE_URL=http://127.0.0.1:1",
        "OPENROUTER_MODEL_1=vendor/cloud", "OPENROUTER_API_KEY=dummy-key",
        "OPENROUTER_BASE_URL=http://127.0.0.1:1/api/v1"})
@AutoConfigureMockMvc
@Import(ReviewsApiTests.FixedProviders.class)
@ExtendWith(OutputCaptureExtension.class)
class ReviewsApiTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired private MockMvc mvc;
    @Autowired private StoryReviewService reviews;
    @Autowired @Qualifier("fixedOllama") private ChatModel local;
    @Autowired @Qualifier("fixedRouter") private ChatModel cloud;

    @BeforeEach
    void fixedResponses() {
        reset(local, cloud);
        when(local.getOptions()).thenReturn(OllamaChatOptions.builder().model("local").build());
        when(cloud.getOptions()).thenReturn(OpenAiChatOptions.builder().model("vendor/cloud").build());
        when(local.call(any(Prompt.class))).thenReturn(answer(ReviewTestData.VALID_JSON));
        when(cloud.call(any(Prompt.class))).thenReturn(answer(ReviewTestData.VALID_JSON));
    }

    @ParameterizedTest
    @CsvSource({"ollama,local", "openrouter,vendor/cloud"})
    void sameTypedContractAndPromptForBothExplicitProviders(String provider, String model) throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(provider, model, ReviewTestData.STORY)))
                .andExpect(status().isOk()).andExpect(content().json(ReviewTestData.VALID_JSON))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.clarificationQuestions.length()").value(0));
        ChatModel selected = provider.equals("ollama") ? local : cloud;
        ChatModel other = provider.equals("ollama") ? cloud : local;
        var capture = ArgumentCaptor.forClass(Prompt.class);
        verify(selected, times(1)).call(capture.capture());
        verifyNoInteractions(other);
        Prompt prompt = capture.getValue();
        assertInstanceOf(SystemMessage.class, prompt.getInstructions().get(0));
        assertTrue(prompt.getInstructions().get(0).getText().contains("fill a quota"));
        assertEquals(ReviewTestData.STORY, prompt.getInstructions().get(1).getText());
        assertEquals(model, prompt.getOptions().getModel());
        assertEquals(1024, prompt.getOptions().getMaxTokens());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 4000})
    void acceptsStoryLengthBoundaries(int length) throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(request("ollama", "local", "s".repeat(length)))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputBeforeAnyModelCall(String input) throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(local, cloud);
    }

    static Stream<String> invalidRequests() {
        return Stream.of("", "{}", "{\"provider\":", request("ollama", "local", " "),
                request("ollama", "local", "short"), request("ollama", "local", "s".repeat(4001)),
                request("", "local", ReviewTestData.STORY), request("ollama", "", ReviewTestData.STORY),
                request("p".repeat(17), "local", ReviewTestData.STORY),
                request("ollama", "m".repeat(201), ReviewTestData.STORY),
                "{\"provider\":\"ollama\",\"model\":\"local\",\"story\":1234567890}",
                "{\"provider\":\"ollama\",\"model\":\"local\",\"story\":null}",
                request("ollama", "local", ReviewTestData.STORY).replaceFirst("\\{", "{\"apiKey\":\"override\","),
                request("ollama", "local", ReviewTestData.STORY) + " {}",
                "{\"provider\":\"ollama\"," + request("ollama", "local", ReviewTestData.STORY).substring(1));
    }

    @ParameterizedTest
    @CsvSource({"ollama,unknown", "openrouter,local", "unknown,local"})
    void rejectsUnknownOrMismatchedModelsBeforeCalls(String provider, String model) throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(request(provider, model, ReviewTestData.STORY)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("MODEL_NOT_AVAILABLE"));
        verifyNoInteractions(local, cloud);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json private-story", "{}", "null", "[]", " ",
            "{\"summary\":\"private-story\",\"acceptanceCriteria\":[],\"clarificationQuestions\":[],\"suggestedTests\":[]}"})
    void invalidOutputIsSafeAndDoesNotRetryOrFallBack(String output, CapturedOutput logs) throws Exception {
        when(local.call(any(Prompt.class))).thenReturn(answer(output));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(request("ollama", "local", "PRIVATE-STORY-MARKER sensitive fictional requirement")))
                .andExpect(status().isBadGateway()).andExpect(content().json("""
                        {"code":"INVALID_PROVIDER_RESPONSE","message":"The selected provider returned an invalid response."}
                        """));
        verify(local, times(1)).call(any(Prompt.class));
        verifyNoInteractions(cloud);
        assertFalse(logs.getAll().contains("PRIVATE-STORY-MARKER"));
        assertFalse(logs.getAll().contains("private-story"));
    }

    @ParameterizedTest
    @CsvSource({"400,502,PROVIDER_REJECTED_REQUEST", "422,502,PROVIDER_REJECTED_REQUEST",
            "401,503,PROVIDER_UNAVAILABLE", "429,429,PROVIDER_RATE_LIMITED",
            "500,503,PROVIDER_UNAVAILABLE", "504,504,PROVIDER_TIMEOUT"})
    void providerFailuresRemainSafeWithoutPaidFallback(int upstream, int status, String code, CapturedOutput logs)
            throws Exception {
        when(local.call(any(Prompt.class))).thenThrow(new RestClientResponseException("PRIVATE-ERROR-KEY",
                HttpStatusCode.valueOf(upstream), "private", HttpHeaders.EMPTY,
                "PRIVATE-ERROR-STORY".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(request("ollama", "local", ReviewTestData.STORY)))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.length()").value(2));
        verify(local, times(1)).call(any(Prompt.class));
        verifyNoInteractions(cloud);
        assertFalse(logs.getAll().contains("PRIVATE-ERROR-KEY"));
        assertFalse(logs.getAll().contains("PRIVATE-ERROR-STORY"));
    }

    @Test
    void socketTimeoutIsSafe() throws Exception {
        when(local.call(any(Prompt.class))).thenThrow(new ResourceAccessException("secret",
                new SocketTimeoutException("private story")));
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(request("ollama", "local", ReviewTestData.STORY)))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"));
        verifyNoInteractions(cloud);
    }

    @Test
    void transportBoundaryAndMethodsAreChecked() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content("x".repeat(65_537)))
                .andExpect(status().is(413));
        mvc.perform(post("/api/reviews").contentType(MediaType.TEXT_PLAIN).content(ReviewTestData.STORY))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/reviews")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(local, cloud);
    }

    @Test
    void directServiceCallCannotBypassInputValidation() {
        assertThrows(ProviderException.class, () -> reviews.review(null));
        assertThrows(ProviderException.class, () -> reviews.review(new ReviewRequest("ollama", "local", " ")));
        verifyNoInteractions(local, cloud);
    }

    private static String request(String provider, String model, String story) {
        return JSON.writeValueAsString(new ReviewRequest(provider, model, story));
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedProviders {
        @Bean ChatModel fixedOllama() { return mock(ChatModel.class, CALLS_REAL_METHODS); }
        @Bean ChatModel fixedRouter() { return mock(ChatModel.class, CALLS_REAL_METHODS); }

        @Bean @Primary
        ProviderChatService fixedProviderChatService(ModelRegistry registry,
                @Qualifier("fixedOllama") ChatModel local, @Qualifier("fixedRouter") ChatModel cloud) {
            return new ProviderChatService(registry, Map.of("ollama", local, "openrouter", cloud), List.of());
        }
    }
}
