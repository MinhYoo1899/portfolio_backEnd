package egovframework.eeum.notification.service.notification;

/**
 * 알림 적재 포트 — 각 업무 도메인 서비스가 <b>자신의 트랜잭션 안에서</b> 호출한다(성공 시 적재, 실패 시 함께 rollback).
 * 구현은 {@code Propagation.MANDATORY} 라 <b>호출자 트랜잭션이 없으면 예외</b>(③b 훅은 업무 tx 안에서만 호출). (도메인 훅=③b)
 *
 * <p>대상 = 유형의 {@link NotificationType#requiredAuthority()} 를 보유한 활성 운영자. {@code ALWAYS} 유형도 <b>권한은
 * 요구</b>하며 토글만 무시한다. 대상유형·딥링크는 {@link NotificationType}이 소유(호출자 지정 불가). eventKey로 수신자별 멱등.
 *
 * <p><b>입력 계약(위반 시 IllegalArgumentException, DB 호출 전 차단):</b>
 * type 필수 / eventKey 1~100자 / title 1~200자 / body 최대 1000자 / targetId 숫자 1~18자리 필수 / 링크 산출 실패 시 적재 금지.
 */
public interface NotificationEmitter {

	/**
	 * 알림 적재(권한 보유 활성 운영자 팬아웃 + 설정 게이팅 + eventKey 멱등).
	 *
	 * @param actorUserSn 이벤트 유발 행위자(등록자, RGTR_SN)
	 * @param eventKey    수신자별 중복방지 키(1~100자)
	 * @param type        알림 유형(필수) — 권한·대상유형·딥링크 소유
	 * @param title       제목(1~200자)
	 * @param body        내용(선택, 최대 1000자)
	 * @param targetId    대상 식별자(숫자 1~18자리, 필수 — 딥링크 산출)
	 */
	void emit(long actorUserSn, String eventKey, NotificationType type, String title, String body, String targetId);
}
