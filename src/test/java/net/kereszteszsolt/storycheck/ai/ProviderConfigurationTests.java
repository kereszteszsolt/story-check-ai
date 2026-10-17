// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ProviderConfiguration.class);

    @Test
    void mapsEnvironmentContractWithoutContactingEitherProvider() {
        runner.withPropertyValues("OLLAMA_BASE_URL=http://127.0.0.1:1", "OLLAMA_MODEL_1= a ",
                "OLLAMA_MODEL_2=a", "OLLAMA_MODEL_3=b", "OLLAMA_MODEL_4=ignored",
                "OPENROUTER_API_KEY= dummy-key ", "OPENROUTER_MODEL_1=vendor/cloud",
                "AI_REQUEST_TIMEOUT_MS=120000").run(context -> {
                    assertThat(context).hasNotFailed();
                    var settings = context.getBean(ProviderProperties.class);
                    assertThat(settings.requestTimeoutMs()).isEqualTo(120000);
                    assertThat(context.getBean(ModelRegistry.class).models()).hasSize(3);
                    assertThat(settings.ollama().baseUrl()).isEqualTo("http://127.0.0.1:1");
                });
    }

    @Test
    void missingKeyDoesNotInitializeRouterEvenWithAnInvalidRouterUrl() {
        runner.withPropertyValues("OLLAMA_MODEL_1=local", "OPENROUTER_API_KEY= ",
                "OPENROUTER_MODEL_1=cloud", "OPENROUTER_BASE_URL=invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ModelRegistry.class).models()).hasSize(1);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999", "120001", "-1"})
    void rejectsOutOfBoundsTimeouts(String timeout) {
        runner.withPropertyValues("AI_REQUEST_TIMEOUT_MS=" + timeout)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsCloudHttpAndEmbeddedCredentialsWithSafeConfigurationErrors() {
        for (String endpoint : new String[] {"http://example.com/api/v1", "https://user:private-key@example.com"}) {
            runner.withPropertyValues("OPENROUTER_API_KEY=dummy-key", "OPENROUTER_MODEL_1=cloud",
                    "OPENROUTER_BASE_URL=" + endpoint).run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasRootCauseMessage(
                                "Invalid provider base URL; use an absolute HTTP(S) endpoint without credentials.");
                    });
        }
    }
}
