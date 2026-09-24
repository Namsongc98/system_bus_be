package com.ticket_system.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Builds the Pageable for list endpoints from the {@code page} and {@code size}
 * query params. Out-of-range values throw IllegalArgumentException, which
 * GlobalExceptionHandler turns into 400; size is rejected, not clamped, so the
 * caller sees why the request failed.
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size);
    }
}
