package egovframework.eeum.account.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 계정/파트너 도메인 오류코드 (DOMAIN_REASON 형식 — conventions.md).
 */
public enum AccountErrorCode implements ErrorCode {

	/** 이미 사용 중인 이메일(활성 계정 기준). */
	ACCOUNT_EMAIL_DUPLICATE("ACCOUNT_EMAIL_DUPLICATE", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),

	/** 필수 약관 미동의. */
	ACCOUNT_TERMS_REQUIRED("ACCOUNT_TERMS_REQUIRED", "필수 약관에 동의해 주세요.", HttpStatus.BAD_REQUEST),

	/** 계정 없음(또는 탈퇴). */
	ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 활성/승인대기 상태가 아닌 계정(정지·반려 등) — 이메일 인증 등 본인 기능 대상이 아님. */
	ACCOUNT_NOT_ACTIVE("ACCOUNT_NOT_ACTIVE", "이용할 수 없는 계정 상태입니다.", HttpStatus.FORBIDDEN),

	/** 본인확인 비밀번호 불일치(탈퇴·비밀번호 변경 등). */
	ACCOUNT_PASSWORD_MISMATCH("ACCOUNT_PASSWORD_MISMATCH", "비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),

	/** 비밀번호 미설정 계정(소셜 전용) — 비밀번호 변경 대상이 아님. */
	ACCOUNT_PASSWORD_NOT_SET("ACCOUNT_PASSWORD_NOT_SET", "비밀번호 로그인 계정이 아닙니다.", HttpStatus.CONFLICT),

	/** 새 비밀번호가 기존 비밀번호와 동일. */
	ACCOUNT_PASSWORD_SAME_AS_OLD("ACCOUNT_PASSWORD_SAME_AS_OLD", "새 비밀번호가 기존 비밀번호와 같습니다.", HttpStatus.BAD_REQUEST),

	/** 가입 기본 역할이 데이터에 없음(시드/설정 불일치 — 운영 설정 오류). */
	ACCOUNT_ROLE_NOT_CONFIGURED("ACCOUNT_ROLE_NOT_CONFIGURED", "가입 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

	/** 동의 변경 대상 약관이 없거나 시행 중이 아님. */
	CONSENT_TERMS_NOT_FOUND("CONSENT_TERMS_NOT_FOUND", "대상 약관을 찾을 수 없거나 시행 중이 아닙니다.", HttpStatus.NOT_FOUND),

	/** 필수약관은 철회/변경 불가(가입 시 동의로 고정 — 선택약관만 변경 가능). */
	CONSENT_ESSENTIAL_NOT_CHANGEABLE("CONSENT_ESSENTIAL_NOT_CHANGEABLE", "필수 약관은 철회/변경할 수 없습니다.", HttpStatus.BAD_REQUEST),

	/** 이미 검증 완료된 이메일 — 재검증 요청 대상이 아님. */
	EMAIL_ALREADY_VERIFIED("EMAIL_ALREADY_VERIFIED", "이미 이메일 인증이 완료되었습니다.", HttpStatus.CONFLICT),

	/** 인증 코드 불일치(또는 챌린지 없음). */
	EMAIL_VERIFICATION_INVALID("EMAIL_VERIFICATION_INVALID", "인증 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 인증 코드 만료 — 재발송 후 다시 시도. */
	EMAIL_VERIFICATION_EXPIRED("EMAIL_VERIFICATION_EXPIRED", "인증 코드가 만료되었습니다. 다시 요청해 주세요.", HttpStatus.GONE),

	/** 인증 코드 검증 시도 횟수 초과 — 챌린지 폐기, 재발송 필요. */
	EMAIL_VERIFICATION_TOO_MANY("EMAIL_VERIFICATION_TOO_MANY", "인증 시도 횟수를 초과했습니다. 다시 요청해 주세요.", HttpStatus.TOO_MANY_REQUESTS),

	/** 재발송 쿨다운 — 잠시 후 다시 요청. */
	EMAIL_VERIFICATION_COOLDOWN("EMAIL_VERIFICATION_COOLDOWN", "잠시 후 다시 요청해 주세요.", HttpStatus.TOO_MANY_REQUESTS),

	/** 일일 발송 한도 초과. */
	EMAIL_VERIFICATION_DAILY_EXCEEDED("EMAIL_VERIFICATION_DAILY_EXCEEDED", "오늘 인증 메일 발송 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

	/** 이미 등록된 사업자등록번호(활성 기준). */
	PARTNER_BRNO_DUPLICATE("PARTNER_BRNO_DUPLICATE", "이미 등록된 사업자등록번호입니다.", HttpStatus.CONFLICT),

	/** 파트너 없음. */
	PARTNER_NOT_FOUND("PARTNER_NOT_FOUND", "파트너를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 이미 승인/반려 처리된 파트너. */
	PARTNER_ALREADY_PROCESSED("PARTNER_ALREADY_PROCESSED", "이미 처리된 파트너 신청입니다.", HttpStatus.CONFLICT),

	/** 파트너 본인 접근 불가(PTNS) — APPROVED+ACTIVE 가 아니거나 본인 파트너 해석 실패. 상태 노출 최소화로 403 통일(P0). */
	PARTNER_NOT_ACCESSIBLE("PARTNER_NOT_ACCESSIBLE", "이용할 수 없는 파트너 상태입니다.", HttpStatus.FORBIDDEN),

	// ---- 운영자 계정관리 (OPERATOR_*) ----

	/** 대상 운영자 없음 — 비OPERATOR 계정도 "없는 것처럼" 404 로 가린다(존재 노출 차단). */
	OPERATOR_NOT_FOUND("OPERATOR_NOT_FOUND", "운영자 계정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 허용되지 않은 운영자 역할 — STAFF/SUPERVISOR 외(임의 ROLE_CD 주입 차단). */
	OPERATOR_INVALID_ROLE("OPERATOR_INVALID_ROLE", "허용되지 않은 운영자 역할입니다.", HttpStatus.BAD_REQUEST),

	/** 자기 자신 대상 금지(비활성·잠금·강등·비번초기화). */
	OPERATOR_CANNOT_MODIFY_SELF("OPERATOR_CANNOT_MODIFY_SELF", "본인 계정에는 수행할 수 없는 작업입니다.", HttpStatus.FORBIDDEN),

	/** SUPER 보유 계정 보호 — 일반 운영자 관리 대상이 아님. */
	OPERATOR_SUPER_PROTECTED("OPERATOR_SUPER_PROTECTED", "보호된 계정에는 수행할 수 없는 작업입니다.", HttpStatus.FORBIDDEN),

	/** 마지막 가용 수퍼바이저 보호 — 가용 SUPERVISOR 0명이 되는 작업 거부. */
	OPERATOR_LAST_SUPERVISOR("OPERATOR_LAST_SUPERVISOR", "마지막 활성 수퍼바이저는 변경할 수 없습니다.", HttpStatus.CONFLICT),

	/** 설정/재설정 토큰 무효(없음·만료·불일치). */
	OPERATOR_SETUP_TOKEN_INVALID("OPERATOR_SETUP_TOKEN_INVALID", "설정 링크가 유효하지 않거나 만료되었습니다.", HttpStatus.BAD_REQUEST),

	/** 계정 상태가 작업 가능한 상태가 아님(전이 조건 불일치). */
	OPERATOR_STATUS_NOT_ALLOWED("OPERATOR_STATUS_NOT_ALLOWED", "현재 계정 상태에서는 수행할 수 없는 작업입니다.", HttpStatus.CONFLICT),

	// ---- 일반회원 관리 (MEMBER_*) ----

	/** 대상 회원 없음 — 비MEMBER 계정도 "없는 것처럼" 404(존재 노출 차단). */
	MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 허용되지 않은 회원 상태변경(동일상태·비가역·전이조건 불일치). */
	MEMBER_STATUS_NOT_ALLOWED("MEMBER_STATUS_NOT_ALLOWED", "현재 상태에서는 수행할 수 없는 상태변경입니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	AccountErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
		this.code = code;
		this.defaultMessage = defaultMessage;
		this.httpStatus = httpStatus;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String defaultMessage() {
		return defaultMessage;
	}

	@Override
	public HttpStatus httpStatus() {
		return httpStatus;
	}
}
