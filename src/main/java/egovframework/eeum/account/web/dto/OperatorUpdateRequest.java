package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운영자 상세 수정 요청 DTO(권한코드 ACNT_MNG) — 부서/직위/연락처.
 *
 * <p>대상은 항상 USER_TYPE_CD='OPERATOR'(서비스 강제). 각 값은 <b>생략(null) 시 기존값 보존</b>(부분수정).
 * 특히 {@code phone} 생략 시 기존 전화번호(암호화/해시)를 그대로 둔다 — 재암호화/해시 재계산 없음.
 * 빈문자열("")과 null 은 모두 "미변경"으로 취급한다(연락처는 필수 필드라 삭제(clear) 의미를 두지 않는다).
 */
@Schema(description = "운영자 상세 수정 요청(부서/직위/연락처 — 생략 시 보존)")
public record OperatorUpdateRequest(

		@Schema(description = "대상 운영자 사용자일련번호", example = "1024")
		@NotBlank(message = "대상 식별자가 필요합니다.")
		@Pattern(regexp = "\\d{1,18}", message = "식별자 형식이 올바르지 않습니다.")
		String userSn,

		@Schema(description = "부서명(생략 시 보존)", example = "운영팀")
		@Size(max = 100, message = "부서명은 100자 이내여야 합니다.")
		String deptNm,

		@Schema(description = "직위명(생략 시 보존)", example = "대리")
		@Size(max = 100, message = "직위명은 100자 이내여야 합니다.")
		String jbpsNm,

		@Schema(description = "휴대전화번호(생략 시 보존). 하이픈 허용, 서버가 정규화·암호화·해시.", example = "010-1234-5678")
		@Size(max = 20, message = "휴대전화번호가 너무 깁니다.")
		String phone) {

	public long userSnAsLong() {
		return Long.parseLong(userSn);
	}
}
