// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ModelRegistryTests {
    static ProviderProperties properties(List<String> ollama, String key, List<String> router) {
        return new ProviderProperties(new ProviderProperties.Ollama("http://127.0.0.1:1", ollama),
                new ProviderProperties.OpenRouter("https://openrouter.ai/api/v1", key, router), 1000);
    }

    @Test
    void emptySlotsYieldAnEmptyImmutableList() {
        var registry = new ModelRegistry(properties(List.of("", " ", ""), "", List.of("", "", "")));
        assertTrue(registry.models().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.models().add(null));
    }

    @Test
    void trimsAndDeduplicatesInSlotOrderWithinEachProvider() {
        var registry = new ModelRegistry(properties(List.of(" a ", "a", " b "), " key ",
                List.of("a", " vendor/b ", "a")));
        assertEquals(List.of(
                new ModelRegistry.ModelChoice("ollama", "a", "Ollama · a"),
                new ModelRegistry.ModelChoice("ollama", "b", "Ollama · b"),
                new ModelRegistry.ModelChoice("openrouter", "a", "OpenRouter · a"),
                new ModelRegistry.ModelChoice("openrouter", "vendor/b", "OpenRouter · vendor/b")), registry.models());
    }

    @Test
    void onlyFirstThreeSlotsPerProviderAreConsidered() {
        var registry = new ModelRegistry(properties(List.of("a", "b", "c", "extra"), "key",
                List.of("a", "b", "c", "extra")));
        assertEquals(6, registry.models().size());
        assertThrows(ProviderException.class, () -> registry.require("ollama", "extra"));
        assertThrows(ProviderException.class, () -> registry.require("openrouter", "extra"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void missingCredentialsHideRouterWithoutHidingOllama(String key) {
        var registry = new ModelRegistry(properties(List.of("local"), key, List.of("cloud")));
        assertEquals(1, registry.models().size());
        assertEquals("local", registry.require("ollama", "local").model());
        assertThrows(ProviderException.class, () -> registry.require("openrouter", "cloud"));
    }

    @Test
    void selectionIsExactAndProviderSpecific() {
        var registry = new ModelRegistry(properties(List.of("local"), "key", List.of("cloud")));
        for (String[] selection : new String[][] {{"other", "local"}, {"ollama", "cloud"},
                {"openrouter", "unknown"}, {"ollama", " local "}, {null, "local"}, {"ollama", null}}) {
            var failure = assertThrows(ProviderException.class,
                    () -> registry.require(selection[0], selection[1]));
            assertEquals(ProviderException.Reason.INVALID_MODEL, failure.reason());
        }
    }

    @Test
    void settingsToStringRedactsCredentials() {
        assertFalse(properties(List.of(), "private-key", List.of()).toString().contains("private-key"));
    }
}
