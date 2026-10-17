// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.web;

import jakarta.validation.Valid;
import net.kereszteszsolt.storycheck.review.ReviewRequest;
import net.kereszteszsolt.storycheck.review.StoryReview;
import net.kereszteszsolt.storycheck.review.StoryReviewService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewsController {
    private final StoryReviewService reviews;

    public ReviewsController(StoryReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping(value = "/api/reviews", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public StoryReview review(@Valid @RequestBody ReviewRequest request) {
        return reviews.review(request);
    }
}
