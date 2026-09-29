package egovframework.eeum.inquiry.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문의글 수정 요청 — 작성자 본인, 답변 전(PENDING)에만 가능.
 *
 * <p>{@code attachments} 는 수정 후 유지할 첨부 전체 목록(목록에 빠진 기존 첨부는 연결해제). null/빈 목록이면 전부 해제.
 */
@Schema(description = "문의글 수정 요청")
public record InqrUpdateRequest(

		@Schema(description = "문의일련번호")
		@NotNull(message = "inqrSn은 필수입니다.")
		Long inqrSn,

		@Schema(description = "문의유형코드")
		@NotBlank(message = "문의유형을 선택해 주세요.")
		@Size(max = 30)
		String inqrTypeCd,

		@Schema(description = "제목")
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200)
		String inqrTtl,

		@Schema(description = "내용")
		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 5000)
		String inqrCn,

		@Schema(description = "비밀글 여부", example = "false")
		Boolean secret,

		@Schema(description = "수정 후 유지할 첨부 전체 목록(빠진 기존 첨부는 연결해제)")
		@Valid
		List<InqrAttachmentRef> attachments) {
}
