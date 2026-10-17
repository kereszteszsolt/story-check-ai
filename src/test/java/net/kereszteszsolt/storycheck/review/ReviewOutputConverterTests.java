// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.review;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import net.kereszteszsolt.storycheck.ai.ProviderException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class ReviewOutputConverterTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ValidatorFactory validation;
    private static ReviewOutputConverter converter;

    @BeforeAll
    static void initialize() {
        validation = Validation.buildDefaultValidatorFactory();
        converter = new ReviewOutputConverter(validation.getValidator());
    }

    @AfterAll
    static void close() {
        validation.close();
    }

    @Test
    void parsesTypedRecordWithZeroQuestionsAndImmutableLists() {
        StoryReview review = converter.convert(ReviewTestData.VALID_JSON);
        assertEquals("The story is clear and testable.", review.summary());
        assertTrue(review.clarificationQuestions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> review.acceptanceCriteria().add("changed"));
    }

    @Test
    void schemaContainsTheSameRequiredFieldsAndBoundsAsValidation() {
        var schema = JSON.readTree(converter.getJsonSchema());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals(4, schema.get("required").size());
        var properties = schema.get("properties");
        assertEquals(4, properties.size());
        assertEquals(600, properties.get("summary").get("maxLength").asInt());
        assertEquals(8, properties.get("acceptanceCriteria").get("maxItems").asInt());
        assertEquals(1, properties.get("suggestedTests").get("minItems").asInt());
        assertEquals(0, properties.get("clarificationQuestions").get("minItems").asInt());
        assertEquals(6, properties.get("clarificationQuestions").get("maxItems").asInt());
        assertEquals(300, properties.get("clarificationQuestions").get("items").get("maxLength").asInt());
        assertTrue(converter.getFormat().contains(converter.getJsonSchema()));
    }

    @Test
    void acceptsAllUpperBoundsAndNonEmptyQuestions() {
        StoryReview maximum = new StoryReview("s".repeat(600), java.util.Collections.nCopies(8, "a".repeat(500)),
                java.util.Collections.nCopies(6, "q".repeat(300)), java.util.Collections.nCopies(8, "t".repeat(500)));
        assertEquals(maximum, converter.convert(JSON.writeValueAsString(maximum)));
    }

    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void rejectsMalformedWrongTypedOrUnboundedOutputWithoutExposingIt(String output) {
        var failure = assertThrows(ProviderException.class, () -> converter.convert(output));
        assertEquals(ProviderException.Reason.INVALID_RESPONSE, failure.reason());
        assertNull(failure.getCause());
        assertEquals("The selected provider returned an invalid response.", failure.getMessage());
    }

    static Stream<String> invalidOutputs() {
        List<String> outputs = new ArrayList<>(List.of("", " ", "null", "[]", "{}", "{\"summary\":",
                "```json\n" + ReviewTestData.VALID_JSON + "\n```",
                "<think>private reasoning</think>" + ReviewTestData.VALID_JSON,
                "Explanation: " + ReviewTestData.VALID_JSON,
                ReviewTestData.VALID_JSON + " {}", "x".repeat(32_769),
                "{\"summary\":\"duplicate\"," + ReviewTestData.VALID_JSON.strip().substring(1)));
        for (String field : List.of("summary", "acceptanceCriteria", "clarificationQuestions", "suggestedTests")) {
            ObjectNode missing = base();
            missing.remove(field);
            outputs.add(JSON.writeValueAsString(missing));
            ObjectNode nil = base();
            nil.putNull(field);
            outputs.add(JSON.writeValueAsString(nil));
        }
        for (Object value : List.of(" ", "s".repeat(601), 42, true)) {
            ObjectNode invalid = base();
            invalid.set("summary", JSON.valueToTree(value));
            outputs.add(JSON.writeValueAsString(invalid));
        }
        for (String field : List.of("acceptanceCriteria", "clarificationQuestions", "suggestedTests")) {
            for (Object value : new Object[] {"not an array", List.of(42), List.of(true), List.of(" "),
                    java.util.Collections.singletonList(null), java.util.Collections.nCopies(9, "item"),
                    List.of("x".repeat(501))}) {
                ObjectNode invalid = base();
                invalid.set(field, JSON.valueToTree(value));
                outputs.add(JSON.writeValueAsString(invalid));
            }
        }
        for (String field : List.of("acceptanceCriteria", "suggestedTests")) {
            ObjectNode invalid = base();
            invalid.putArray(field);
            outputs.add(JSON.writeValueAsString(invalid));
        }
        ObjectNode extra = base();
        extra.put("rawProviderReply", "private-key raw-story");
        outputs.add(JSON.writeValueAsString(extra));
        ObjectNode questions = base();
        questions.set("clarificationQuestions", JSON.valueToTree(java.util.Collections.nCopies(7, "question")));
        outputs.add(JSON.writeValueAsString(questions));
        questions.set("clarificationQuestions", JSON.valueToTree(List.of("q".repeat(301))));
        outputs.add(JSON.writeValueAsString(questions));
        outputs.add("{\"summary\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}");
        return outputs.stream();
    }

    private static ObjectNode base() {
        return (ObjectNode) JSON.readTree(ReviewTestData.VALID_JSON);
    }
}
