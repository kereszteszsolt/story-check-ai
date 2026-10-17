// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.web;

import java.util.List;
import net.kereszteszsolt.storycheck.ai.ModelRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelsController {
    private final ModelRegistry registry;

    public ModelsController(ModelRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/api/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelsResponse models() {
        return new ModelsResponse(registry.models());
    }

    public record ModelsResponse(List<ModelRegistry.ModelChoice> models) {}
}
