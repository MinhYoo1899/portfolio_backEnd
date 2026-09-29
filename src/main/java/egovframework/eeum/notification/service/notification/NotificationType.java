package egovframework.eeum.notification.service.notification;

/**
 * 운영자 알림 유형(8종) — 게이팅·<b>수신 권한</b>·대상유형·딥링크를 <b>유형이 소유</b>한다(호출자가 임의 지정 불가).
 *
 * <ul>
 *   <li>{@link Gate#ALWAYS} 는 <b>설정 토글만</b> 무시(항상 생성)한다 — <b>권한은 무시하지 않는다</b>(무권한자는 미수신).</li>
 *   <li>{@code requiredAuthority} = 이 알림을 받을 수 있는 권한코드(AUTH_CD). 적재 대상 = 이 권한 보유 활성 운영자.</li>
 *   <li>{@code targetType} = 대상 엔티티 유형(서버 고정), {@link #link}(targetId) = OPS 딥링크(서버 allowlist, 숫자ID만).</li>
 * </ul>
 */
public enum NotificationType {

	PARTNER_AUTH(Gate.ALWAYS, "PRTNR_APRV", "PARTNER", "/partner/"),
	PRODUCT_REVIEW(Gate.ALWAYS, "HOUSE_REVIEW", "HOUSE", "/product?houseSn="),
	RESERVATION(Gate.RESERVATION, "RESERVATION_VIEW", "RESERVATION", "/reservation?reservationId="),
	INQUIRY(Gate.INQUIRY, "INQR_ANS", "INQUIRY", "/inquiry?inquiryId="),
	SETTLEMENT(Gate.SETTLEMENT, "SETTLEMENT_VIEW", "SETTLEMENT", "/settlement?settlementId="),
	COUPON(Gate.MARKETING, "COUPON_VIEW", "COUPON", "/coupon?couponId="),
	NOTICE(Gate.MARKETING, "NOTICE_VIEW", "NOTICE", "/notice?noticeId="),
	DM(Gate.MARKETING, "DM_VIEW", "DM", "/dm?dmId=");

	/** 설정 게이트 — 어떤 토글이 이 유형의 생성을 좌우하는지(ALWAYS=토글 무시). */
	public enum Gate {
		ALWAYS, RESERVATION, INQUIRY, SETTLEMENT, MARKETING
	}

	private final Gate gate;
	private final String requiredAuthority;
	private final String targetType;
	private final String linkPrefix;

	NotificationType(Gate gate, String requiredAuthority, String targetType, String linkPrefix) {
		this.gate = gate;
		this.requiredAuthority = requiredAuthority;
		this.targetType = targetType;
		this.linkPrefix = linkPrefix;
	}

	public Gate gate() {
		return gate;
	}

	/** 이 알림을 수신할 수 있는 권한코드(AUTH_CD) — 적재 대상 필터. */
	public String requiredAuthority() {
		return requiredAuthority;
	}

	/** 대상 엔티티 유형(서버 고정 — 호출자 지정 불가). */
	public String targetType() {
		return targetType;
	}

	/**
	 * OPS 딥링크 산출 — 서버 allowlist 고정 템플릿 + <b>숫자 targetId</b>만(경로/쿼리 주입 방지). 아니면 {@code null}.
	 */
	public String link(String targetId) {
		if (targetId == null || !targetId.matches("\\d{1,18}")) {
			return null;
		}
		return linkPrefix + targetId;
	}

	/**
	 * 수신자의 설정(4종 boolean)에 비추어 이 유형을 생성해야 하는지(설정 게이팅). ALWAYS=항상 true.
	 * ※ 권한 필터는 적재 쿼리(대상 조회)에서 별도 적용 — 여기선 토글만 판정.
	 */
	public boolean enabledFor(boolean reservationAlert, boolean inquiryAlert,
			boolean settlementAlert, boolean marketingAlert) {
		return switch (gate) {
			case ALWAYS -> true;
			case RESERVATION -> reservationAlert;
			case INQUIRY -> inquiryAlert;
			case SETTLEMENT -> settlementAlert;
			case MARKETING -> marketingAlert;
		};
	}
}
