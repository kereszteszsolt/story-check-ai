// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;

public final class ProviderChatService implements AutoCloseable {
    static final int MAX_OUTPUT_TOKENS = 1024;
    private final ModelRegistry registry;
    private final Map<String, ChatClient> clients;
    private final List<AutoCloseable> resources;

    ProviderChatService(ModelRegistry registry, Map<String, ChatModel> adapters,
            List<AutoCloseable> resources) {
        this.registry = registry;
        this.clients = adapters.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> ChatClient.create(entry.getValue())));
        this.resources = List.copyOf(resources);
    }

    public String complete(String provider, String model, String prompt) {
        ModelRegistry.ModelChoice choice = registry.require(provider, model);
        if (prompt == null || prompt.isBlank() || prompt.length() > 16_000) {
            throw new ProviderException(ProviderException.Reason.INVALID_INPUT);
        }
        try {
            String result = clients.get(choice.provider()).prompt(new Prompt(prompt))
                    .options(ChatOptions.builder().model(choice.model()).maxTokens(MAX_OUTPUT_TOKENS))
                    .call().content();
            if (result == null || result.isBlank() || result.length() > 32_768) {
                throw new ProviderException(ProviderException.Reason.INVALID_RESPONSE);
            }
            return result;
        }
        catch (RuntimeException failure) {
            throw ProviderException.from(failure);
        }
    }

    public <T> T structured(String provider, String model, Prompt prompt, StructuredOutputConverter<T> converter) {
        ModelRegistry.ModelChoice choice = registry.require(provider, model);
        if (prompt == null || prompt.getContents().isBlank()
                || prompt.getContents().length() + converter.getFormat().length() > 16_000) {
            throw new ProviderException(ProviderException.Reason.INVALID_INPUT);
        }
        try {
            T result = clients.get(choice.provider()).prompt(prompt)
                    .options(ChatOptions.builder().model(choice.model()).maxTokens(MAX_OUTPUT_TOKENS))
                    .call().entity(converter, options -> options.useProviderStructuredOutput());
            if (result == null) {
                throw new ProviderException(ProviderException.Reason.INVALID_RESPONSE);
            }
            return result;
        }
        catch (RuntimeException failure) {
            throw ProviderException.from(failure);
        }
    }

    @Override
    public void close() throws Exception {
        for (AutoCloseable resource : resources) {
            resource.close();
        }
    }
}
