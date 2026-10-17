// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.review;

import jakarta.validation.Validator;
import net.kereszteszsolt.storycheck.ai.ProviderException;
import net.kereszteszsolt.storycheck.config.JsonConfiguration;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public final class ReviewOutputConverter extends BeanOutputConverter<StoryReview> {
    private static final JsonMapper JSON = mapper();
    private final Validator validator;

    public ReviewOutputConverter(Validator validator) {
        // Accept only the promised JSON document, without removing prose or reasoning.
        super(StoryReview.class, JSON, text -> text);
        this.validator = validator;
    }

    private static JsonMapper mapper() {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(32_768).maxNumberLength(32).build()).build();
        var builder = JsonMapper.builder(factory);
        JsonConfiguration.configure(builder);
        return builder.build();
    }

    @Override
    protected String generateSchema() {
        ObjectNode schema = (ObjectNode) JSON.readTree(super.generateSchema());
        schema.put("additionalProperties", false);
        schema.putArray("required").add("summary").add("acceptanceCriteria")
                .add("clarificationQuestions").add("suggestedTests");
        ObjectNode properties = (ObjectNode) schema.get("properties");
        stringBounds((ObjectNode) properties.get("summary"), StoryReview.SUMMARY_MAX);
        listBounds((ObjectNode) properties.get("acceptanceCriteria"), 1, StoryReview.ITEMS_MAX, StoryReview.ITEM_MAX);
        listBounds((ObjectNode) properties.get("clarificationQuestions"), 0, StoryReview.QUESTIONS_MAX, StoryReview.QUESTION_MAX);
        listBounds((ObjectNode) properties.get("suggestedTests"), 1, StoryReview.ITEMS_MAX, StoryReview.ITEM_MAX);
        return JSON.writeValueAsString(schema);
    }

    private void listBounds(ObjectNode schema, int min, int max, int itemMax) {
        schema.put("minItems", min).put("maxItems", max);
        stringBounds((ObjectNode) schema.get("items"), itemMax);
    }

    private void stringBounds(ObjectNode schema, int max) {
        schema.put("minLength", 1).put("maxLength", max).put("pattern", "\\S");
    }

    @Override
    public StoryReview convert(String text) {
        try {
            if (text == null || text.isBlank() || text.length() > 32_768) {
                throw new IllegalArgumentException();
            }
            StoryReview review = super.convert(text);
            if (review == null || !validator.validate(review).isEmpty()) {
                throw new IllegalArgumentException();
            }
            return review;
        }
        catch (RuntimeException invalid) {
            // Parser/validation exceptions can contain the raw story or model response.
            throw new ProviderException(ProviderException.Reason.INVALID_RESPONSE);
        }
    }
}
