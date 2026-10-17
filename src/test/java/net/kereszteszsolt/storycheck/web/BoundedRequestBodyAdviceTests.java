// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
package net.kereszteszsolt.storycheck.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.ErrorResponseException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedRequestBodyAdviceTests {
    private final BoundedRequestBodyAdvice advice = new BoundedRequestBodyAdvice();

    @Test
    void acceptsExactByteLimitWithoutContentLength() throws Exception {
        byte[] body = new byte[65_536];
        MockHttpInputMessage input = new MockHttpInputMessage(body);
        assertEquals(-1, input.getHeaders().getContentLength());
        assertArrayEquals(body, advice.beforeBodyRead(input, null, null, null).getBody().readAllBytes());
    }

    @Test
    void rejectsActualBytesWhenContentLengthIsAbsentOrUnderstated() {
        for (long declaredLength : new long[] {-1, 10}) {
            MockHttpInputMessage input = new MockHttpInputMessage(new byte[65_537]);
            if (declaredLength >= 0) {
                input.getHeaders().setContentLength(declaredLength);
            }
            ErrorResponseException error = assertThrows(ErrorResponseException.class,
                    () -> advice.beforeBodyRead(input, null, null, null));
            assertEquals(HttpStatus.CONTENT_TOO_LARGE, error.getStatusCode());
        }
    }

    @Test
    void rejectsOversizedDeclaredLengthBeforeReading() {
        MockHttpInputMessage input = new MockHttpInputMessage(new byte[0]);
        input.getHeaders().setContentLength(65_537);
        ErrorResponseException error = assertThrows(ErrorResponseException.class,
                () -> advice.beforeBodyRead(input, null, null, null));
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, error.getStatusCode());
    }
}
