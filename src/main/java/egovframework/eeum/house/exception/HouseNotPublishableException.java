package egovframework.eeum.house.exception;

import java.util.List;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.ErrorDetail;

/**
 * 운영개시(발행) 게이트 미충족 — 미충족 조건 전체를 담아 던진다(전체 수집 방식).
 *
 * <p>{@link BusinessException}(errorCode={@link HouseErrorCode#HOUSE_NOT_PUBLISHABLE}, 409) 하위라
 * 기존 처리/테스트와 호환되며, {@link ErrorDetail} 구현으로 응답 {@code result.failedConditions} 까지 제공한다.
 * {@code resultMessage} 는 기존 형식(기본문구 + 첫 미충족 사유)을 유지한다.
 */
public class HouseNotPublishableException extends BusinessException implements ErrorDetail {

	private final transient PublishGateResult publishGateResult;

	public HouseNotPublishableException(List<PublishCondition> failedConditions) {
		super(HouseErrorCode.HOUSE_NOT_PUBLISHABLE, summaryMessage(failedConditions));
		this.publishGateResult = PublishGateResult.from(failedConditions);
	}

	@Override
	public Object detail() {
		return publishGateResult;
	}

	/** 테스트/소비용 — 미충족 조건 목록. */
	public List<PublishGateConditionFailure> failedConditions() {
		return publishGateResult.failedConditions();
	}

	/** resultMessage = 기존 형식 유지: 기본문구 + 첫 미충족 사유. 전체 사유는 {@code result.failedConditions}. */
	private static String summaryMessage(List<PublishCondition> failedConditions) {
		String base = HouseErrorCode.HOUSE_NOT_PUBLISHABLE.defaultMessage();
		return failedConditions.isEmpty() ? base : base + " (" + failedConditions.get(0).message() + ")";
	}
}
