package egovframework.eeum.notification.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 운영자 알림 VO (OPERATOR_NOTIFICATION — 수신자별 1행).
 */
@Getter
@Setter
public class OperatorNotificationVO {

	/** 운영자알림일련번호 */
	private Long operatorNotiSn;

	/** 수신자(운영자) 사용자일련번호 */
	private Long rcvrUserSn;

	/** 알림유형코드 */
	private String notiTypeCd;

	/** 이벤트키(수신자별 중복방지) */
	private String eventKey;

	/** 제목 */
	private String notiTtl;

	/** 내용 */
	private String notiBody;

	/** 대상 엔티티 유형 */
	private String trgtTypeCd;

	/** 대상 엔티티 식별자 */
	private String trgtId;

	/** 이동 링크(서버 allowlist 매핑값) */
	private String linkUrl;

	/** 읽음여부(Y/N) */
	private String readYn;

	/** 등록자(이벤트 유발 행위자) */
	private Long rgtrSn;

	/** 등록일시 */
	private LocalDateTime regDt;
}
