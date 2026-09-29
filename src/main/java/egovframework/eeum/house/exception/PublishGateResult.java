package egovframework.eeum.house.exception;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영개시 게이트 실패 응답 {@code result} payload — 미충족 조건 전체 목록(전체 수집 방식).
 *
 * <p>HTTP 409 + {@code resultCode=HOUSE_NOT_PUBLISHABLE} 와 함께 내려간다. {@code resultMessage} 는 기존 형식
 * (기본문구 + 첫 미충족 사유)을 유지하고, 전체 사유는 이 목록으로 제공한다.
 */
@Schema(description = "운영개시 게이트 실패 상세(미충족 조건 목록)")
public record PublishGateResult(List<PublishGateConditionFailure> failedConditions) {

	/** 방어복사 — 외부 리스트 변경이 응답 DTO에 영향을 주지 않게 불변 리스트로 고정. */
	public PublishGateResult {
		failedConditions = List.copyOf(failedConditions);
	}

	public static PublishGateResult from(List<PublishCondition> conditions) {
		return new PublishGateResult(conditions.stream().map(PublishGateConditionFailure::from).toList());
	}
}
