package com.fnbx.shared.utils;

import org.springframework.data.domain.Page;

import java.util.function.Function;

public final class PaginationUtils {

    private PaginationUtils() {}

    public static <S, T> PagedResponse<T> toPagedResponse(
            Page<S> page,
            Function<? super S, T> mapper) {
        return PagedResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
