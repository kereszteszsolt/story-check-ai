// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFoundationTests.ValidationFixture.class)
class ApiFoundationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthRequiresNoProviderConfiguration() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }

    @Test
    void wrongMethodPreservesAllowHeaderAndReturnsSafeJson() throws Exception {
        mvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedResponseFormatHasStableError() throws Exception {
        mvc.perform(get("/api/health").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/missing", "/api/models", "/api/reviews"})
    void unimplementedRoutesAreNotFound(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validRequestCanReachController() throws Exception {
        mvc.perform(post("/api/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"example\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("example"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":\" \"}", "{\"text\":\"12345678901234567\"}",
            "{\"text\":", ""})
    void invalidJsonOrConstraintsHaveSameNonSensitiveShape(String body) throws Exception {
        mvc.perform(post("/api/test/validation").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"The request is invalid."}
                        """));
    }

    @Test
    void unsupportedContentTypeHasStableError() throws Exception {
        mvc.perform(post("/api/test/validation").contentType(MediaType.TEXT_PLAIN).content("secret"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void oversizedBodyIsRejectedBeforeJsonConversion() throws Exception {
        mvc.perform(post("/api/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(65_537)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void unexpectedFailureHidesExceptionDetails() throws Exception {
        mvc.perform(get("/api/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"The request could not be completed."}
                        """));
    }

    @RestController
    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationFixture {
        @PostMapping("/api/test/validation")
        SampleInput validate(@Valid @RequestBody SampleInput input) {
            return input;
        }

        @GetMapping("/api/test/failure")
        void fail() {
            throw new IllegalStateException("secret-provider-key /private/file raw-story");
        }
    }

    record SampleInput(@NotBlank @Size(max = 16) String text) {}
}
