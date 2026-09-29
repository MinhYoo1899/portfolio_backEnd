package egovframework.eeum.base.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 계약. 공통({@link CommonErrorCode})과 도메인별 오류코드가 함께 구현한다.
 * {@code code()}는 envelope 의 resultCode(예: {@code RESERVATION_NOT_FOUND}), {@code httpStatus()}는 HTTP 상태.
 */
public interface ErrorCode {

	/** 결과코드 문자열(DOMAIN_REASON, 대문자 스네이크). */
	String code();

	/** 기본 메시지. */
	String defaultMessage();

	/** HTTP 상태코드. */
	HttpStatus httpStatus();
}
