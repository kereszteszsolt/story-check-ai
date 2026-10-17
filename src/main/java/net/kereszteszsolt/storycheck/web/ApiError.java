// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.web;

import org.springframework.http.HttpStatusCode;

public record ApiError(String code, String message) {
    static ApiError fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new ApiError("INVALID_REQUEST", "The request is invalid.");
            case 404 -> new ApiError("NOT_FOUND", "The requested resource was not found.");
            case 405 -> new ApiError("METHOD_NOT_ALLOWED", "The HTTP method is not supported.");
            case 406 -> new ApiError("NOT_ACCEPTABLE", "The requested response format is not supported.");
            case 413 -> new ApiError("PAYLOAD_TOO_LARGE", "The request body exceeds 65536 bytes.");
            case 415 -> new ApiError("UNSUPPORTED_MEDIA_TYPE", "The request content type is not supported.");
            default -> status.is5xxServerError()
                    ? new ApiError("INTERNAL_ERROR", "The request could not be completed.")
                    : new ApiError("INVALID_REQUEST", "The request is invalid.");
        };
    }
}
