package egovframework.eeum.base.response;

import java.util.List;

/**
 * 서버사이드 페이지네이션 응답(architecture D18). 그리드의 "총 N건/페이지번호"를 위해 {@code total} 포함.
 *
 * @param <T> 항목 타입(타입드 DTO/VO)
 */
public record PageResponse<T>(
		List<T> items,
		long total,
		int page,
		int pageSize,
		int totalPages) {

	/** items 불변화 + 음수 방지(컴팩트 생성자). */
	public PageResponse {
		items = items == null ? List.of() : List.copyOf(items);
		total = Math.max(total, 0);
		page = Math.max(page, 1);
		pageSize = Math.max(pageSize, 0);
		totalPages = Math.max(totalPages, 0);
	}

	/**
	 * @param items    현재 페이지 항목(null 허용 → 빈 목록)
	 * @param total    전체 건수(음수 → 0)
	 * @param page     현재 페이지(1-base, 음수 → 1)
	 * @param pageSize 페이지 크기(양수)
	 */
	public static <T> PageResponse<T> of(List<T> items, long total, int page, int pageSize) {
		int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) Math.max(total, 0) / pageSize);
		return new PageResponse<>(items, total, page, pageSize, totalPages);
	}
}
