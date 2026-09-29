package egovframework.eeum.notification.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 운영자 알림 설정 VO (OPERATOR_NOTI_SETTING — 1:1, PK=USER_SN).
 *
 * <p>토글 4종(예약/문의/정산/마케팅)을 Y/N 문자로 보관. 행 부재 = 기본값(서비스가 반환).
 */
@Getter
@Setter
public class OperatorNotiSettingVO {

	/** 사용자일련번호(운영자) */
	private Long userSn;

	/** 예약 알림 수신여부(Y/N) */
	private String rsvtNotiYn;

	/** 문의 알림 수신여부(Y/N) */
	private String inqrNotiYn;

	/** 정산 알림 수신여부(Y/N) */
	private String stlmNotiYn;

	/** 마케팅 알림 수신여부(Y/N) */
	private String mktgNotiYn;

	/** 등록일시 */
	private LocalDateTime regDt;

	/** 수정일시 */
	private LocalDateTime mdfcnDt;
}
