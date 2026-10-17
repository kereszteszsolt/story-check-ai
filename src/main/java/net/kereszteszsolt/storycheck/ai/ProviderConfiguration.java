// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfiguration {
    @Bean
    ModelRegistry modelRegistry(ProviderProperties properties) {
        return new ModelRegistry(properties);
    }

    @Bean(destroyMethod = "close")
    ProviderChatService providerChatService(ProviderProperties properties, ModelRegistry registry) {
        Map<String, ChatModel> adapters = new HashMap<>();
        List<AutoCloseable> resources = new ArrayList<>();
        for (ModelRegistry.ModelChoice choice : registry.models()) {
            if (adapters.containsKey(choice.provider())) {
                continue;
            }
            if (choice.provider().equals("ollama")) {
                String baseUrl = endpoint(properties.ollama().baseUrl(), false);
                HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
                resources.add(http::close);
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(properties.timeout());
                OllamaApi api = OllamaApi.builder().baseUrl(baseUrl)
                        .restClientBuilder(RestClient.builder().requestFactory(factory))
                        .responseErrorHandler(new ResponseErrorHandler() {
                            @Override
                            public boolean hasError(ClientHttpResponse response) throws IOException {
                                return response.getStatusCode().isError();
                            }

                            @Override
                            public void handleError(URI url, HttpMethod method, ClientHttpResponse response)
                                    throws IOException {
                                throw ProviderException.forStatus(response.getStatusCode().value());
                            }
                        }).build();
                adapters.put("ollama", OllamaChatModel.builder().ollamaApi(api)
                        .options(OllamaChatOptions.builder().model(choice.model())
                                .numPredict(ProviderChatService.MAX_OUTPUT_TOKENS).build())
                        .modelManagementOptions(ModelManagementOptions.defaults())
                        .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())).build());
            }
            else {
                String baseUrl = endpoint(properties.openrouter().baseUrl(), true);
                var transport = SpringAiOpenAiHttpClient.builder().timeout(properties.timeout()).build();
                var client = new OpenAIClientImpl(ClientOptions.builder().httpClient(transport).baseUrl(baseUrl)
                        .apiKey(properties.openrouter().apiKey().trim())
                        .timeout(properties.timeout()).maxRetries(0).build());
                resources.add(client::close);
                adapters.put("openrouter", OpenAiChatModel.builder().openAiClient(client)
                        .openAiClientAsync(client.async())
                        .options(OpenAiChatOptions.builder().baseUrl(baseUrl)
                                .apiKey(properties.openrouter().apiKey().trim()).model(choice.model())
                                .timeout(properties.timeout()).maxRetries(0)
                                .maxTokens(ProviderChatService.MAX_OUTPUT_TOKENS).toolChoice("none")
                                .extraBody(Map.of("provider", Map.of("allow_fallbacks", false))).build())
                        .build());
            }
        }
        return new ProviderChatService(registry, adapters, resources);
    }

    private String endpoint(String value, boolean cloud) {
        try {
            URI uri = URI.create(value.trim());
            boolean https = "https".equals(uri.getScheme());
            boolean http = "http".equals(uri.getScheme());
            boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(https || http && (!cloud || loopback))) {
                throw new IllegalArgumentException();
            }
            return uri.toString().replaceAll("/+$", "");
        }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid provider base URL; use an absolute HTTP(S) endpoint without credentials.");
        }
    }
}
