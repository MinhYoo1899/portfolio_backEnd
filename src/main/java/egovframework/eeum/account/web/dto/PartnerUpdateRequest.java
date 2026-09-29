package egovframework.eeum.account.web.dto;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 파트너 사업자정보 수정 요청(운영자 PRTNR_APRV) — partial update.
 *
 * <p>null/미포함 필드는 변경하지 않는다. {@code conm/brno/rprsvNm/addr/picNm/prtnrEmlAddr} 는 공백("") 전송 시 검증오류(클리어 금지),
 * {@code daddr}/{@code mailOrderSalesNo}/{@code picMblTelno} 는 공백 클리어 허용(선택 필드). 상태(승인/계정)는 이 API 범위가 아니다.
 * {@code picMblTelno}(담당자 연락처)는 암호화 저장되며 응답에서는 마스킹값만 노출된다.
 * brno 가 실제 변경되면 서비스가 사업자번호검증상태를 초기화(REQUESTED)한다.
 */
@Schema(description = "파트너 사업자정보 수정 요청(부분수정)")
public record PartnerUpdateRequest(

		@Schema(description = "파트너일련번호(문자열)")
		@NotBlank(message = "파트너일련번호가 필요합니다.")
		String prtnrSn,

		@Schema(description = "상호(미포함=변경안함, 공백 불가)", example = "고즈넉한옥")
		@Size(max = 200, message = "상호는 200자 이내여야 합니다.")
		String conm,

		@Schema(description = "사업자등록번호(숫자 10자리, 미포함=변경안함)", example = "1234567890")
		@Pattern(regexp = "\\d{10}", message = "사업자등록번호는 숫자 10자리여야 합니다.")
		String brno,

		@Schema(description = "대표자명(미포함=변경안함, 공백 불가)", example = "김대표")
		@Size(max = 100, message = "대표자명은 100자 이내여야 합니다.")
		String rprsvNm,

		@Schema(description = "사업장주소(미포함=변경안함, 공백 불가)")
		@Size(max = 320, message = "주소는 320자 이내여야 합니다.")
		String addr,

		@Schema(description = "사업장상세주소(미포함=변경안함, 공백=클리어 허용)")
		@Size(max = 200, message = "상세주소는 200자 이내여야 합니다.")
		String daddr,

		@Schema(description = "통신판매업신고번호(선택, 미포함=변경안함, 공백=클리어 허용, 40자 이내)", example = "2026-서울강남-01234")
		@Size(max = 40, message = "통신판매업신고번호는 40자 이내여야 합니다.")
		String mailOrderSalesNo,

		@Schema(description = "담당자 휴대전화번호(선택, 미포함=변경안함, 공백=클리어 허용, 20자 이내 — 암호화 저장/마스킹 응답)", example = "010-1234-5678")
		@Size(max = 20, message = "담당자 휴대전화번호는 20자 이내여야 합니다.")
		String picMblTelno,

		@Schema(description = "담당자명(선택, 미포함=변경안함, 공백 불가)", example = "김담당")
		@Size(max = 100, message = "담당자명은 100자 이내여야 합니다.")
		String picNm,

		@Schema(description = "파트너 대표 이메일(선택, 미포함=변경안함, 공백 불가)", example = "biz@hanok.kr")
		@Email(message = "이메일 형식이 아닙니다.")
		@Size(max = 150, message = "이메일은 150자 이내여야 합니다.")
		String prtnrEmlAddr) {

	/** 식별자 파싱(형식 오류 시 400). */
	public long prtnrSnAsLong() {
		try {
			return Long.parseLong(prtnrSn);
		} catch (NumberFormatException e) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "식별자 형식이 올바르지 않습니다.");
		}
	}
}
