// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.review;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoryReview(
        @NotBlank @Size(max = StoryReview.SUMMARY_MAX) String summary,
        @NotNull @Size(min = 1, max = StoryReview.ITEMS_MAX)
        List<@NotBlank @Size(max = StoryReview.ITEM_MAX) String> acceptanceCriteria,
        @NotNull @Size(max = StoryReview.QUESTIONS_MAX)
        List<@NotBlank @Size(max = StoryReview.QUESTION_MAX) String> clarificationQuestions,
        @NotNull @Size(min = 1, max = StoryReview.ITEMS_MAX)
        List<@NotBlank @Size(max = StoryReview.ITEM_MAX) String> suggestedTests) {
    public static final int SUMMARY_MAX = 600;
    public static final int ITEMS_MAX = 8;
    public static final int ITEM_MAX = 500;
    public static final int QUESTIONS_MAX = 6;
    public static final int QUESTION_MAX = 300;

    public StoryReview {
        if (acceptanceCriteria != null) acceptanceCriteria = List.copyOf(acceptanceCriteria);
        if (clarificationQuestions != null) clarificationQuestions = List.copyOf(clarificationQuestions);
        if (suggestedTests != null) suggestedTests = List.copyOf(suggestedTests);
    }
}
