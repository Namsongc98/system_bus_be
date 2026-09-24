package com.ticket_system.common.Dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Response body for every paged list endpoint. {@code page} is 0-based, like
 * Spring Data. Build it with {@link #from(Page)} instead of returning a raw
 * {@code Page}, whose JSON shape is not a stable contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResponse {
        content = content == null ? List.of() : content;
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }

    /**
     * Maps each element, e.g. entity to response DTO. The numbers come from the
     * page accessors, not from getPageable(): an unpaged Pageable throws on
     * getPageSize().
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream()
                .<T>map(mapper)
                .toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
