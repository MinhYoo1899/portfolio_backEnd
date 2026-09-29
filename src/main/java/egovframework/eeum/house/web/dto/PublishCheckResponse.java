package egovframework.eeum.house.web.dto;

import java.util.List;

import egovframework.eeum.house.exception.PublishCondition;
import egovframework.eeum.house.exception.PublishGateConditionFailure;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영개시 사전점검(publish-check) 결과 — 상태변경 없이 현재 발행 가능 여부를 read-only 로 알려준다.
 *
 * <p>{@code publishable=true} 면 {@code failedConditions} 는 빈 배열. 미충족 항목은 발행 실패(409)와 동일한
 * {@link PublishGateConditionFailure}(code/message/fixableOn) 구조를 재사용한다.
 */
@Schema(description = "운영개시 사전점검 결과")
public record PublishCheckResponse(
		@Schema(description = "발행 가능 여부") boolean publishable,
		@Schema(description = "미충족 조건(빈 배열 = 발행 가능)") List<PublishGateConditionFailure> failedConditions) {

	public static PublishCheckResponse from(List<PublishCondition> failedConditions) {
		return new PublishCheckResponse(
				failedConditions.isEmpty(),
				failedConditions.stream().map(PublishGateConditionFailure::from).toList());
	}
}
