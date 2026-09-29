package egovframework.eeum.account.web.dto;

/**
 * 운영자 역할(요청 입력) — 클라이언트가 보내는 안전한 enum.
 *
 * <p>요청 body 의 raw ROLE_CD/ROLE_SN 신뢰 금지 — 서버가 이 enum 만 받아 허용 ROLE_CD 로 매핑한다.
 * SUPER 등 다른 역할은 이 API 로 부여/회수 불가(enum 에 없음).
 */
public enum OperatorRole {

	/** 운영자 일반직원 → ROLE_CD = OPERATOR_STAFF */
	STAFF("OPERATOR_STAFF"),

	/** 운영자 수퍼바이저 → ROLE_CD = OPERATOR_SUPERVISOR (STAFF 동시 부여 강제) */
	SUPERVISOR("OPERATOR_SUPERVISOR");

	private final String roleCd;

	OperatorRole(String roleCd) {
		this.roleCd = roleCd;
	}

	/** 매핑된 ROLE_MST.ROLE_CD. */
	public String roleCd() {
		return roleCd;
	}
}
