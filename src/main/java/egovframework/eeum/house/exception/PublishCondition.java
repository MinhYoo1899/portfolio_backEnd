package egovframework.eeum.house.exception;

/**
 * 운영개시(발행) 게이트 9조건 — 코드/사유메시지/해결위치(fixableOn) 단일 출처.
 *
 * <p>{@code HousePublishValidator}(검증)와 응답 DTO({@code PublishGateConditionFailure})가 같은 enum 을 참조해
 * 코드/메시지 드리프트를 막는다. (※ 통신판매업신고번호는 현재 게이트 조건 아님 — TODO 유지, 추가 금지.)
 */
public enum PublishCondition {

	PARTNER_NOT_APPROVED("PARTNER_NOT_APPROVED", "소유 파트너가 승인된 활성 상태가 아닙니다.", FixableOn.PARTNER),
	PARTNER_BUSINESS_INFO_MISSING("PARTNER_BUSINESS_INFO_MISSING", "판매자 사업자정보(상호/사업자등록번호/대표자명/주소)가 비어 있습니다.", FixableOn.PARTNER),
	HOUSE_BASIC_INFO_MISSING("HOUSE_BASIC_INFO_MISSING", "고택 기본정보(고택명/우편번호/주소/소개)가 비어 있습니다.", FixableOn.HOUSE),
	CHECKIN_CHECKOUT_TIME_MISSING("CHECKIN_CHECKOUT_TIME_MISSING", "체크인/체크아웃 시각이 설정되지 않았습니다.", FixableOn.HOUSE),
	RESERVATION_CONFIRM_TYPE_MISSING("RESERVATION_CONFIRM_TYPE_MISSING", "예약확정방식이 설정되지 않았습니다.", FixableOn.HOUSE),
	CANCELLATION_POLICY_MISSING("CANCELLATION_POLICY_MISSING", "취소·환불규정이 작성되지 않았습니다.", FixableOn.HOUSE),
	PAYMENT_METHOD_MISSING("PAYMENT_METHOD_MISSING", "결제수단이 1개 이상 설정되어야 합니다.", FixableOn.HOUSE),
	REPRESENTATIVE_IMAGE_MISSING("REPRESENTATIVE_IMAGE_MISSING", "대표이미지가 1개 이상 등록되어야 합니다.", FixableOn.HOUSE),
	PUBLISHABLE_ROOM_MISSING("PUBLISHABLE_ROOM_MISSING", "게시 가능한 객실(활성·재고·욕실·침구·이미지·미래 판매가능 캘린더 완비)이 1개 이상 필요합니다.", FixableOn.ROOM);

	/**
	 * 프론트 분기용 — 어느 화면에서 해결 가능한지.
	 * {@code SYSTEM} 은 향후 데이터 정합/운영자 조치성 실패용 예약값(이번 9조건에는 사용하지 않음).
	 */
	public enum FixableOn {
		PARTNER, HOUSE, ROOM, SYSTEM
	}

	/** API 계약 코드 — Java enum 이름과 분리(리네임이 곧 API 파괴가 되지 않도록). 현재 값은 enum 이름과 동일하게 고정. */
	private final String code;
	private final String message;
	private final FixableOn fixableOn;

	PublishCondition(String code, String message, FixableOn fixableOn) {
		this.code = code;
		this.message = message;
		this.fixableOn = fixableOn;
	}

	public String code() {
		return code;
	}

	public String message() {
		return message;
	}

	public FixableOn fixableOn() {
		return fixableOn;
	}
}
