package egovframework.eeum.base.exception;

/**
 * 입력 검증 실패 시 필드별 오류. (거절값은 보안상 미노출 — conventions.md §2)
 */
public record FieldErrorDetail(String field, String message) {
}
