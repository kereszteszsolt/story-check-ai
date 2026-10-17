// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank @Size(max = 16) String provider,
        @NotBlank @Size(max = 200) String model,
        @NotBlank @Size(min = 10, max = 4000) String story) {}
