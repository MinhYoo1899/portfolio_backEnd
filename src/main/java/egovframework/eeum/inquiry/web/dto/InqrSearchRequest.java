package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 문의 목록 조회 요청 — 두 모드.
 *
 * <ul>
 *   <li><b>커서 모드(무한 스크롤)</b>: {@code cursor}(마지막 본 INQR_SN) 지정 시. 첫 페이지는 cursor 생략.</li>
 *   <li><b>오프셋 모드(그리드)</b>: cursor 없이 {@code page}/{@code pageSize}.</li>
 * </ul>
 * 정렬 최신순(INQR_SN DESC) 고정. 필터: mine/inqrType/ansStts/keyword(제목).
 */
@Schema(description = "문의 목록 조회 요청")
public record InqrSearchRequest(

		@Schema(description = "페이지(오프셋 모드, 1부터)", example = "1")
		Integer page,

		@Schema(description = "페이지 크기(오프셋 모드, 최대 100)", example = "20")
		Integer pageSize,

		@Schema(description = "커서(무한스크롤 — 마지막 본 inqrSn). 지정 시 커서 모드")
		Long cursor,

		@Schema(description = "커서 모드 청크 크기(최대 100)", example = "20")
		Integer size,

		@Schema(description = "내 글만(로그인 필요)", example = "false")
		Boolean mine,

		@Schema(description = "문의유형 필터")
		@Size(max = 30)
		String inqrType,

		@Schema(description = "답변상태 필터(PENDING/ANSWERED)")
		@Size(max = 20)
		String ansStts,

		@Schema(description = "제목 검색어")
		@Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
		String keyword) {

	/** 커서 모드 — cursor(이어보기) 또는 size(첫 페이지 청크) 중 하나라도 있으면 무한스크롤(codex 코드검토). */
	@Schema(hidden = true)
	public boolean isCursorMode() {
		return cursor != null || size != null;
	}
}
