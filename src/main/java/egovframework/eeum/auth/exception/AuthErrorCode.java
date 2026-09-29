package egovframework.eeum.auth.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 인증 도메인 오류코드 (DOMAIN_REASON 형식 — conventions.md §resultCode).
 *
 * <p>계정 존재 여부 열거(enumeration) 방지: 미존재/비밀번호 불일치/탈퇴는 모두
 * {@link #AUTH_INVALID_CREDENTIALS} 하나로 응답한다.
 */
public enum AuthErrorCode implements ErrorCode {

	/** 이메일 또는 비밀번호 불일치(미존재·탈퇴 포함 — 열거 방지 위해 단일화). */
	AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),

	/** 정지된 계정. */
	AUTH_ACCOUNT_SUSPENDED("AUTH_ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다.", HttpStatus.FORBIDDEN),

	/** 가입 반려된 계정(파트너 반려 등). */
	AUTH_ACCOUNT_REJECTED("AUTH_ACCOUNT_REJECTED", "가입이 반려된 계정입니다.", HttpStatus.FORBIDDEN),

	/** refresh 토큰 무효(만료/회수/재사용 탐지 포함). 클라이언트는 재로그인으로 처리. */
	AUTH_REFRESH_INVALID("AUTH_REFRESH_INVALID", "다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),

	/** 소셜 id_token 검증 실패(서명/만료/issuer/audience 불일치, 미설정 등). 열거 방지 위해 단일 메시지. */
	AUTH_SOCIAL_TOKEN_INVALID("AUTH_SOCIAL_TOKEN_INVALID", "소셜 인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),

	/** OAuth state 무효(만료/재사용/위조 통합 — 콜백·result 교환에서). 상세 비노출. */
	OAUTH_STATE_INVALID("OAUTH_STATE_INVALID", "요청이 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.", HttpStatus.BAD_REQUEST),

	/** 지원하지 않는 소셜 provider(authorize/callback). */
	OAUTH_PROVIDER_UNSUPPORTED("OAUTH_PROVIDER_UNSUPPORTED", "지원하지 않는 소셜 제공자입니다.", HttpStatus.BAD_REQUEST),

	/** result 토큰 무효(만료/재사용/위조 통합). 상세 비노출. */
	OAUTH_RESULT_INVALID("OAUTH_RESULT_INVALID", "요청이 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	AuthErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
