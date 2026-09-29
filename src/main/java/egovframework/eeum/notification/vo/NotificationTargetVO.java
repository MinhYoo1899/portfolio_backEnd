package egovframework.eeum.notification.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 알림 적재 대상 투영 — 활성 운영자 USER_SN + 각자의 알림설정(②, Y/N; 설정 행 없으면 null → 기본값 적용).
 */
@Getter
@Setter
public class NotificationTargetVO {

	private Long userSn;
	private String rsvtNotiYn;
	private String inqrNotiYn;
	private String stlmNotiYn;
	private String mktgNotiYn;
}
