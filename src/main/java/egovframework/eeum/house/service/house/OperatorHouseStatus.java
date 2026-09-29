package egovframework.eeum.house.service.house;

/**
 * 운영자 상품(고택) 상태 — 실제 {@code HOUSE_STTS_CD} 값과 화면 라벨의 단일 진실원(SSOT).
 *
 * <p>프론트가 라벨을 임의로 하드코딩하지 않도록 filter-options 계약이 이 enum을 그대로 내려준다.
 * value = 실제 상태코드(분기 기준), label = 표시용. 검수요청(REVIEW_REQUESTED)은 실제 상태머신으로
 * 도입됨(2026-07-06) — filter-options 의 {@code unsupported.reviewStatus=false}.
 * 순서 = 드롭다운(조회 필터) 노출 순서. ★조회 상태 목록 ≠ 전이 가능 상태(전이는 상태별 전용 API).
 */
public enum OperatorHouseStatus {

	DRAFT("임시저장"),
	REVIEW_REQUESTED("검수요청"),
	ACTIVE("게재중"),
	HIDDEN("숨김"),
	SUSPENDED("게재 중지");

	private final String label;

	OperatorHouseStatus(String label) {
		this.label = label;
	}

	/** 실제 상태코드(값 비교/분기 기준) — enum 이름과 동일. */
	public String value() {
		return name();
	}

	/** 화면 표시용 라벨(운영 중 변경 가능 — 분기 금지). */
	public String label() {
		return label;
	}
}
