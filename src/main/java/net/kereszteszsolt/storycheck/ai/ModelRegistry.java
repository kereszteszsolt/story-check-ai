// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.util.ArrayList;
import java.util.List;

public final class ModelRegistry {
    private final List<ModelChoice> models;

    public ModelRegistry(ProviderProperties properties) {
        List<ModelChoice> choices = new ArrayList<>();
        add(choices, "ollama", "Ollama", properties.ollama().models());
        if (properties.openrouter().enabled()) {
            add(choices, "openrouter", "OpenRouter", properties.openrouter().models());
        }
        models = List.copyOf(choices);
    }

    public List<ModelChoice> models() {
        return models;
    }

    public ModelChoice require(String provider, String model) {
        return models.stream().filter(choice -> choice.provider().equals(provider)
                        && choice.model().equals(model))
                .findFirst().orElseThrow(() -> new ProviderException(ProviderException.Reason.INVALID_MODEL));
    }

    private void add(List<ModelChoice> choices, String provider, String label, List<String> slots) {
        if (slots == null) {
            return;
        }
        slots.stream().limit(3).filter(value -> value != null).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct()
                .forEach(model -> choices.add(new ModelChoice(provider, model, label + " · " + model)));
    }

    public record ModelChoice(String provider, String model, String label) {}
}
