package com.comfy.caseclose.utils;

import com.comfy.caseclose.dto.response.PagedResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static <T, U> PagedResponse<U> toPagedResponse(Page<T> page, Function<T, U> mapper) {
        List<U> content = page.getContent().stream()
                .map(mapper)
                .toList();

        return PagedResponse.<U>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
