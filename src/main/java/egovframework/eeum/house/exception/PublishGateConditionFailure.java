package egovframework.eeum.house.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영개시 게이트 미충족 조건 1건 — 프론트가 화면별로 분기할 수 있는 구조화 항목.
 */
@Schema(description = "운영개시 게이트 미충족 조건")
public record PublishGateConditionFailure(
		@Schema(description = "조건 코드", example = "REPRESENTATIVE_IMAGE_MISSING") String code,
		@Schema(description = "사유 메시지") String message,
		@Schema(description = "해결 위치(PARTNER/HOUSE/ROOM/SYSTEM)", example = "HOUSE") String fixableOn) {

	public static PublishGateConditionFailure from(PublishCondition condition) {
		return new PublishGateConditionFailure(condition.code(), condition.message(), condition.fixableOn().name());
	}
}
