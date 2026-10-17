// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck;

import net.kereszteszsolt.storycheck.ai.ModelRegistry;
import net.kereszteszsolt.storycheck.ai.ProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"OLLAMA_MODEL_1= local ", "OLLAMA_MODEL_2=local", "OLLAMA_MODEL_3=other",
        "OPENROUTER_API_KEY=private-key", "OPENROUTER_MODEL_1=vendor/cloud", "OPENROUTER_MODEL_2=vendor/cloud",
        "OPENROUTER_MODEL_3=vendor/other", "OLLAMA_BASE_URL=http://127.0.0.1:1",
        "OPENROUTER_BASE_URL=https://private-endpoint.invalid/api/v1"})
@AutoConfigureMockMvc
@Import(ModelsApiTests.SelectionFixture.class)
class ModelsApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void returnsOnlyPublicConfiguredDescriptorsWithoutCallingProviders() throws Exception {
        mvc.perform(get("/api/models")).andExpect(status().isOk())
                .andExpect(content().json("""
                        {"models":[
                        {"provider":"ollama","model":"local","label":"Ollama · local"},
                        {"provider":"ollama","model":"other","label":"Ollama · other"},
                        {"provider":"openrouter","model":"vendor/cloud","label":"OpenRouter · vendor/cloud"},
                        {"provider":"openrouter","model":"vendor/other","label":"OpenRouter · vendor/other"}]}
                        """))
                .andExpect(content().string(not(containsString("private-key"))))
                .andExpect(content().string(not(containsString("private-endpoint"))));
    }

    @ParameterizedTest
    @CsvSource({"ollama,unknown", "openrouter,local", "other,local"})
    void invalidSelectionReturnsSafe422(String provider, String model) throws Exception {
        mvc.perform(get("/api/test/select").param("provider", provider).param("model", model))
                .andExpect(status().is(422))
                .andExpect(content().json("""
                        {"code":"MODEL_NOT_AVAILABLE","message":"Select an enabled provider and model."}
                        """));
    }

    @ParameterizedTest
    @CsvSource({"TIMEOUT,504,PROVIDER_TIMEOUT", "RATE_LIMITED,429,PROVIDER_RATE_LIMITED",
            "UNAVAILABLE,503,PROVIDER_UNAVAILABLE", "REJECTED,502,PROVIDER_REJECTED_REQUEST",
            "INVALID_RESPONSE,502,INVALID_PROVIDER_RESPONSE"})
    void safeProviderErrorsUseConsistentApiShape(ProviderException.Reason reason, int status, String code)
            throws Exception {
        mvc.perform(get("/api/test/provider-error").param("reason", reason.name()))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.length()").value(2));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class SelectionFixture {
        private final ModelRegistry registry;

        SelectionFixture(ModelRegistry registry) {
            this.registry = registry;
        }

        @GetMapping("/api/test/select")
        ModelRegistry.ModelChoice select(@RequestParam String provider, @RequestParam String model) {
            return registry.require(provider, model);
        }

        @GetMapping("/api/test/provider-error")
        void providerError(@RequestParam ProviderException.Reason reason) {
            throw new ProviderException(reason);
        }
    }
}
