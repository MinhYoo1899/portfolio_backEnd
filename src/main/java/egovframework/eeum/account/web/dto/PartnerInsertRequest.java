package egovframework.eeum.account.web.dto;

import java.util.List;

import egovframework.eeum.account.web.dto.TrmsAgreItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 파트너(사업자) 가입 신청 요청 DTO — 계정 + 사업자 정보.
 *
 * <p>가입 직후 계정상태 PENDING + 파트너 승인상태 REQUESTED — 운영자 승인 후 ACTIVE/APPROVED.
 */
@Schema(description = "파트너 가입 신청 요청")
public record PartnerInsertRequest(

		// ---- 로그인 계정 ----
		@Schema(description = "이메일주소(로그인 ID)", example = "partner@hanok.kr")
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 아닙니다.")
		@Size(max = 150, message = "이메일은 150자 이내여야 합니다.")
		String emlAddr,

		@Schema(description = "비밀번호 (8~64자)")
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
		String pswd,

		@Schema(description = "가입자명(실명)", example = "김파트너")
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이내여야 합니다.")
		String userNm,

		// ---- 사업자 정보 ----
		@Schema(description = "상호(사업자명)", example = "고즈넉한옥")
		@NotBlank(message = "상호를 입력해 주세요.")
		@Size(max = 200, message = "상호는 200자 이내여야 합니다.")
		String conm,

		@Schema(description = "사업자등록번호(숫자 10자리, 하이픈 없이)", example = "1234567890")
		@NotBlank(message = "사업자등록번호를 입력해 주세요.")
		@Pattern(regexp = "\\d{10}", message = "사업자등록번호는 숫자 10자리여야 합니다.")
		String brno,

		@Schema(description = "대표자명", example = "김대표")
		@Size(max = 100, message = "대표자명은 100자 이내여야 합니다.")
		String rprsvNm,

		@Schema(description = "사업장주소")
		@Size(max = 320, message = "주소는 320자 이내여야 합니다.")
		String addr,

		@Schema(description = "사업장상세주소")
		@Size(max = 200, message = "상세주소는 200자 이내여야 합니다.")
		String daddr,

		@Schema(description = "통신판매업신고번호(선택, 40자 이내)", example = "2026-서울강남-01234")
		@Size(max = 40, message = "통신판매업신고번호는 40자 이내여야 합니다.")
		String mailOrderSalesNo,

		@Schema(description = "담당자명")
		@Size(max = 100, message = "담당자명은 100자 이내여야 합니다.")
		String picNm,

		@Schema(description = "담당자 휴대전화번호(선택, 20자 이내 — 암호화 저장)", example = "010-1234-5678")
		@Size(max = 20, message = "담당자 휴대전화번호는 20자 이내여야 합니다.")
		String picMblTelno,

		@Schema(description = "파트너 대표 이메일(회사)")
		@Email(message = "이메일 형식이 아닙니다.")
		@Size(max = 150, message = "이메일은 150자 이내여야 합니다.")
		String prtnrEmlAddr,

		// ---- 약관 ----
		@Schema(description = "약관 동의 목록 (시행 중 필수약관 전부 Y 필요)")
		@NotEmpty(message = "약관 동의 정보가 필요합니다.")
		@Valid
		List<TrmsAgreItem> trmsAgreList,

		@Schema(description = "동의채널코드(WEB/APP — 기본 WEB)", example = "WEB", defaultValue = "WEB")
		@Pattern(regexp = "WEB|APP", message = "동의채널은 WEB 또는 APP만 허용됩니다.")
		String agreChnlCd) {

	public String agreChnlOrDefault() {
		return agreChnlCd == null || agreChnlCd.isBlank() ? "WEB" : agreChnlCd;
	}
}
