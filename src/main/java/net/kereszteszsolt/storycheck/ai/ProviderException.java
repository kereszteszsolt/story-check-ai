// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.ai;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.web.client.RestClientResponseException;

public final class ProviderException extends RuntimeException {
    private final Reason reason;

    public ProviderException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    static ProviderException from(RuntimeException failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof ProviderException safe) {
                return safe;
            }
            if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return new ProviderException(Reason.TIMEOUT);
            }
            if (cause instanceof OpenAIServiceException response) {
                return forStatus(response.statusCode());
            }
            if (cause instanceof RestClientResponseException response) {
                return forStatus(response.getStatusCode().value());
            }
        }
        return new ProviderException(Reason.UNAVAILABLE);
    }

    static ProviderException forStatus(int status) {
        return new ProviderException(switch (status) {
            case 408, 504 -> Reason.TIMEOUT;
            case 429 -> Reason.RATE_LIMITED;
            case 400, 422 -> Reason.REJECTED;
            default -> Reason.UNAVAILABLE;
        });
    }

    public enum Reason {
        INVALID_MODEL(422, "MODEL_NOT_AVAILABLE", "Select an enabled provider and model."),
        INVALID_INPUT(400, "INVALID_REQUEST", "The request is invalid."),
        TIMEOUT(504, "PROVIDER_TIMEOUT", "The selected provider did not respond in time."),
        RATE_LIMITED(429, "PROVIDER_RATE_LIMITED", "The selected provider is rate limited."),
        UNAVAILABLE(503, "PROVIDER_UNAVAILABLE", "The selected provider is unavailable."),
        REJECTED(502, "PROVIDER_REJECTED_REQUEST", "The selected provider rejected the request."),
        INVALID_RESPONSE(502, "INVALID_PROVIDER_RESPONSE", "The selected provider returned an invalid response.");

        private final int status;
        private final String code;
        private final String message;

        Reason(int status, String code, String message) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

        public int status() { return status; }
        public String code() { return code; }
        public String message() { return message; }
    }
}
