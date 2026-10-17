// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderChatServiceTests {
    private final ChatModel local = mock(ChatModel.class, CALLS_REAL_METHODS);
    private final ChatModel cloud = mock(ChatModel.class, CALLS_REAL_METHODS);
    private final ModelRegistry registry = new ModelRegistry(ModelRegistryTests.properties(
            List.of("first", "selected"), "key", List.of("vendor/cloud")));
    private final ProviderChatService service = new ProviderChatService(registry,
            Map.of("ollama", local, "openrouter", cloud), List.of());

    @Test
    void unknownModelsAndProvidersCannotCallAnAdapter() {
        assertThrows(ProviderException.class, () -> service.complete("ollama", "unknown", "hello"));
        assertThrows(ProviderException.class, () -> service.complete("other", "selected", "hello"));
        verifyNoInteractions(local, cloud);
    }

    @Test
    void routesOnlyToExplicitProviderAndPreservesLiteralPrompt() {
        String prompt = "Literal {story} and ${not_a_template}";
        when(local.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt request = invocation.getArgument(0);
            assertEquals("selected", request.getOptions().getModel());
            assertEquals(1024, request.getOptions().getMaxTokens());
            assertEquals(prompt, request.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("fixed answer"))));
        });
        assertEquals("fixed answer", service.complete("ollama", "selected", prompt));
        verify(local, times(1)).call(any(Prompt.class));
        verifyNoInteractions(cloud);
    }

    @Test
    void timeoutDoesNotFallBackToCloudOrExposeCause() {
        when(local.call(any(Prompt.class))).thenThrow(new ResourceAccessException("private-key raw-story",
                new SocketTimeoutException("private-key")));
        var failure = assertThrows(ProviderException.class, () -> service.complete("ollama", "selected", "hello"));
        assertEquals(ProviderException.Reason.TIMEOUT, failure.reason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private-key"));
        verify(local, times(1)).call(any(Prompt.class));
        verifyNoInteractions(cloud);
    }

    @Test
    void rejectsEmptyAndOversizedOutput() {
        for (String output : new String[] {" ", "x".repeat(32_769)}) {
            when(local.call(any(Prompt.class))).thenReturn(
                    new ChatResponse(List.of(new Generation(new AssistantMessage(output)))));
            var failure = assertThrows(ProviderException.class,
                    () -> service.complete("ollama", "selected", "hello"));
            assertEquals(ProviderException.Reason.INVALID_RESPONSE, failure.reason());
        }
        verifyNoInteractions(cloud);
    }

    @Test
    void boundsInputBeforeAnyProviderCall() {
        for (String input : new String[] {null, " ", "x".repeat(16_001)}) {
            var failure = assertThrows(ProviderException.class,
                    () -> service.complete("ollama", "selected", input));
            assertEquals(ProviderException.Reason.INVALID_INPUT, failure.reason());
        }
        verifyNoInteractions(local, cloud);
    }
}
