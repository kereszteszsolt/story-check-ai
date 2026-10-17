// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.time.Duration;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("storycheck.ai")
public record ProviderProperties(Ollama ollama, OpenRouter openrouter,
        @Min(1000) @Max(120000) long requestTimeoutMs) {
    public Duration timeout() {
        return Duration.ofMillis(requestTimeoutMs);
    }

    public record Ollama(String baseUrl, List<String> models) {}

    public record OpenRouter(String baseUrl, String apiKey, List<String> models) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "OpenRouter[credentials=redacted]";
        }
    }
}
