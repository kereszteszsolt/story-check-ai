// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.review;

public final class ReviewTestData {
    public static final String STORY = "As a user, I want to copy literal {code} so that I can reuse it.";
    public static final String VALID_JSON = """
            {"summary":"The story is clear and testable.",
             "acceptanceCriteria":["The original text is copied unchanged."],
             "clarificationQuestions":[],
             "suggestedTests":["Copy text containing braces and Unicode characters."]}
            """;

    private ReviewTestData() {}
}
