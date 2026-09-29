package egovframework.eeum.notification.service.notification.impl;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import egovframework.eeum.notification.service.notification.dao.OperatorNotificationMapper;
import egovframework.eeum.notification.vo.NotificationTargetVO;
import egovframework.eeum.notification.vo.OperatorNotificationVO;
import lombok.RequiredArgsConstructor;

/**
 * 알림 적재 포트 구현 — <b>권한 보유</b> 활성 운영자 팬아웃 + 수신자별 설정 게이팅 + eventKey 멱등.
 *
 * <p>{@code Propagation.MANDATORY} — 반드시 호출자(업무) 트랜잭션 안에서 호출되어야 하며, 없으면 예외.
 * 업무 트랜잭션이 실패하면 적재된 알림도 함께 rollback 된다. 대상유형·딥링크는 {@link NotificationType}이 소유하고
 * 호출자는 지정할 수 없다. 입력은 DB 조회 전에 검증한다(계약 위반 = IllegalArgumentException).
 */
@Component
@RequiredArgsConstructor
public class NotificationEmitterImpl implements NotificationEmitter {

	private static final int EVENT_KEY_MAX = 100;
	private static final int TITLE_MAX = 200;
	private static final int BODY_MAX = 1000;

	private final OperatorNotificationMapper notificationMapper;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void emit(long actorUserSn, String eventKey, NotificationType type, String title, String body, String targetId) {
		// --- 입력 검증(DB 호출 전 차단) ---
		if (type == null) {
			throw new IllegalArgumentException("type is required");
		}
		requireLength(eventKey, 1, EVENT_KEY_MAX, "eventKey");
		requireLength(title, 1, TITLE_MAX, "title");
		if (body != null && body.length() > BODY_MAX) {
			throw new IllegalArgumentException("body length must be <= " + BODY_MAX);
		}
		if (targetId == null || !targetId.matches("\\d{1,18}")) {
			throw new IllegalArgumentException("targetId must be numeric (1~18 digits)");
		}
		String linkUrl = type.link(targetId);
		if (linkUrl == null) {
			// 링크 산출 실패 시 적재 금지(allowlist 밖) — 여기까지 오면 계약 위반
			throw new IllegalArgumentException("linkUrl resolution failed for targetId");
		}
		String targetType = type.targetType();

		// --- 권한 보유 활성 운영자 팬아웃 + 설정 게이팅 ---
		List<NotificationTargetVO> targets =
				notificationMapper.selectActiveOperatorTargets(type.requiredAuthority());
		for (NotificationTargetVO t : targets) {
			boolean enabled = type.enabledFor(
					on(t.getRsvtNotiYn(), true),
					on(t.getInqrNotiYn(), true),
					on(t.getStlmNotiYn(), false),
					on(t.getMktgNotiYn(), false));
			if (!enabled) {
				continue;
			}
			OperatorNotificationVO vo = new OperatorNotificationVO();
			vo.setRcvrUserSn(t.getUserSn());
			vo.setNotiTypeCd(type.name());
			vo.setEventKey(eventKey);
			vo.setNotiTtl(title);
			vo.setNotiBody(body);
			vo.setTrgtTypeCd(targetType);
			vo.setTrgtId(targetId);
			vo.setLinkUrl(linkUrl);
			vo.setRgtrSn(actorUserSn);
			notificationMapper.insertIfAbsent(vo); // UNIQUE(RCVR,TYPE,EVENT_KEY) → 재적재 멱등
		}
	}

	private static void requireLength(String value, int min, int max, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		int len = value.length();
		if (len < min || len > max) {
			throw new IllegalArgumentException(name + " length must be " + min + ".." + max);
		}
	}

	/** 설정값(Y/N) 해석 — 행 없음(null)은 기본값. */
	private static boolean on(String yn, boolean dflt) {
		return yn == null ? dflt : "Y".equals(yn);
	}
}
