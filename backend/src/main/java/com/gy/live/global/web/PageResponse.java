package com.gy.live.global.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Spring의 Page를 그대로 직렬화하지 않고, 프론트가 쓰는 필드만 고정된 형식으로 내보낸다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}
}
