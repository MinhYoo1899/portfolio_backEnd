package egovframework.eeum.account.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일반회원 가입 요청 DTO.
 *
 * <p>가입 직후 계정상태는 ACTIVE (전화번호 본인인증(포트원) 연동 전 임시 정책 — 연동 시 인증 완료 후 ACTIVE 로 변경 예정).
 */
@Schema(description = "일반회원 가입 요청")
public record AccountInsertRequest(

		@Schema(description = "이메일주소(로그인 ID)", example = "user@eeum.kr")
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 아닙니다.")
		@Size(max = 150, message = "이메일은 150자 이내여야 합니다.")
		String emlAddr,

		@Schema(description = "비밀번호 (8~64자, 영문+숫자+특수문자 중 2종 이상)")
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
		String pswd,

		@Schema(description = "사용자명(실명)", example = "홍길동")
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이내여야 합니다.")
		String userNm,

		@Schema(description = "별명(닉네임)", example = "한옥지기")
		@Size(max = 30, message = "별명은 30자 이내여야 합니다.")
		String ncnm,

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
