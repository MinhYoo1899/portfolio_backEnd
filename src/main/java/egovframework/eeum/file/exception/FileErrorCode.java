package egovframework.eeum.file.exception;

import org.springframework.http.HttpStatus;

import egovframework.eeum.base.exception.ErrorCode;

/**
 * 파일첨부 도메인 오류코드 (DOMAIN_REASON 형식 — conventions.md §resultCode).
 */
public enum FileErrorCode implements ErrorCode {

	/** 빈 파일 업로드. */
	FILE_EMPTY("FILE_EMPTY", "파일이 비어 있습니다.", HttpStatus.BAD_REQUEST),

	/** 허용되지 않은 확장자. */
	FILE_EXTENSION_NOT_ALLOWED("FILE_EXTENSION_NOT_ALLOWED", "허용되지 않은 파일 형식입니다.", HttpStatus.BAD_REQUEST),

	/** 매직바이트 검사 실패(확장자 위조 등 — 내용과 형식 불일치). */
	FILE_CONTENT_INVALID("FILE_CONTENT_INVALID", "파일 내용이 형식과 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

	/** 크기 한도 초과(413). */
	FILE_TOO_LARGE("FILE_TOO_LARGE", "파일 크기가 허용 한도를 초과했습니다.", HttpStatus.PAYLOAD_TOO_LARGE),

	/** 이미지 해상도(픽셀 수) 한도 초과 — 픽셀폭탄/디코딩 자원고갈 방어(413). */
	FILE_DIMENSION_TOO_LARGE("FILE_DIMENSION_TOO_LARGE", "이미지 해상도가 허용 한도를 초과했습니다.", HttpStatus.PAYLOAD_TOO_LARGE),

	/** 업로드 쿼터 초과 — 최근 24시간 건수/용량 상한(저장소 고갈 방어, 429). */
	FILE_QUOTA_EXCEEDED("FILE_QUOTA_EXCEEDED", "업로드 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS),

	/** 존재하지 않는/삭제된 파일. */
	FILE_NOT_FOUND("FILE_NOT_FOUND", "파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 삭제 권한 없음(등록자 본인/운영자 아님). */
	FILE_DELETE_FORBIDDEN("FILE_DELETE_FORBIDDEN", "이 파일을 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN),

	/** 저장소(SFTP) 처리 오류 — 연결/전송 실패 등. */
	FILE_STORAGE_ERROR("FILE_STORAGE_ERROR", "파일 저장소 처리 중 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),

	/** 저장소 일시 불가 — 미설정/풀 고갈/호스트키 미검증(fail-safe). */
	FILE_STORAGE_UNAVAILABLE("FILE_STORAGE_UNAVAILABLE", "파일 저장소를 일시적으로 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),

	/** 첨부 연결 검증 실패 — 타인 파일/타 용도/이미 연결/만료/삭제 등(6중검증 거부, 열거 방지 위해 단일 400). */
	FILE_ATTACHMENT_INVALID("FILE_ATTACHMENT_INVALID", "첨부할 수 없는 파일이 포함되어 있습니다.", HttpStatus.BAD_REQUEST),

	/** 첨부 개수 초과(문의당 최대 N개). */
	FILE_ATTACHMENT_COUNT_EXCEEDED("FILE_ATTACHMENT_COUNT_EXCEEDED", "첨부 가능한 파일 개수를 초과했습니다.", HttpStatus.BAD_REQUEST),

	/** 첨부 총 용량 초과(문의당 합계 상한). */
	FILE_ATTACHMENT_SIZE_EXCEEDED("FILE_ATTACHMENT_SIZE_EXCEEDED", "첨부 파일의 총 용량이 허용 한도를 초과했습니다.", HttpStatus.PAYLOAD_TOO_LARGE);
	// 비밀글+PUBLIC 은 400 차단이 아니라 연결 시 PRIVATE 자동 보정(하이브리드 A안 확정 2026-06-18) — 별도 에러코드 불필요

	private final String code;
	private final String defaultMessage;
	private final HttpStatus httpStatus;

	FileErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
