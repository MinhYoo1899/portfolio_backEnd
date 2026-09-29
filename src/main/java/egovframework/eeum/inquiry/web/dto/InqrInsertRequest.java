package egovframework.eeum.inquiry.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 문의글 작성 요청.
 *
 * @param secret      비밀글 여부(미지정 시 유형별 기본값 — 예약/결제/계정은 기본 비밀)
 * @param attachments 연결할 임시 첨부 목록(선업로드된 atchFileSn + 희망 공개성). 비밀글이면 PRIVATE 강제.
 */
@Schema(description = "문의글 작성 요청")
public record InqrInsertRequest(

		@Schema(description = "문의유형코드(공통코드 INQR_TYPE)", example = "RESERVATION")
		@NotBlank(message = "문의유형을 선택해 주세요.")
		@Size(max = 30)
		String inqrTypeCd,

		@Schema(description = "제목")
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이내여야 합니다.")
		String inqrTtl,

		@Schema(description = "내용")
		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 5000, message = "내용은 5000자 이내여야 합니다.")
		String inqrCn,

		@Schema(description = "비밀글 여부(미지정 시 유형별 기본값)", example = "false")
		Boolean secret,

		@Schema(description = "연결할 첨부 목록(임시 업로드된 atchFileSn + 희망 공개성)")
		@Valid
		List<InqrAttachmentRef> attachments) {
}
