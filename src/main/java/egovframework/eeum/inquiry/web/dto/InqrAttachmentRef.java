package egovframework.eeum.inquiry.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 문의 작성/수정 시 연결할 첨부 참조 — 임시 업로드된 첨부의 식별자 + 희망 공개성.
 *
 * <p>공개성(visibility)은 공개글에서만 의미가 있다(기본 PUBLIC). 비밀글이면 서버가 PRIVATE 로 강제한다.
 */
@Schema(description = "연결할 첨부(임시 업로드된 atchFileSn + 희망 공개성)")
public record InqrAttachmentRef(

		@Schema(description = "첨부파일일련번호(임시 업로드 결과)")
		@NotNull(message = "atchFileSn은 필수입니다.")
		Long atchFileSn,

		@Schema(description = "희망 공개성(PUBLIC/PRIVATE, 미지정 시 PUBLIC). 비밀글이면 PRIVATE 강제", example = "PUBLIC")
		String visibility) {
}
