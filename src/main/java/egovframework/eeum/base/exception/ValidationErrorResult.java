package egovframework.eeum.base.exception;

import java.util.List;

/**
 * 검증 실패 응답의 {@code result} 본문. (conventions.md §2: {@code result.fieldErrors})
 */
public record ValidationErrorResult(List<FieldErrorDetail> fieldErrors) {
}
