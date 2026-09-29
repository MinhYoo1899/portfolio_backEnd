package egovframework.eeum.notification.service.notification.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.notification.vo.NotificationTargetVO;
import egovframework.eeum.notification.vo.OperatorNotificationVO;

/**
 * 운영자 알림함 매퍼 — XML: {@code egovframework/mapper/eeum/notification/OperatorNotificationMapper_mysql.xml}
 */
@Mapper
public interface OperatorNotificationMapper {

	/**
	 * 적재 대상 = <b>지정 권한 보유</b> 활성·미삭제 OPERATOR + 각자의 알림설정(②, 없으면 NULL → 서비스 기본값).
	 * 권한 필터(AUTH_CD)는 유형의 {@code requiredAuthority} — ALWAYS 유형도 권한은 요구한다.
	 */
	List<NotificationTargetVO> selectActiveOperatorTargets(@Param("requiredAuthority") String requiredAuthority);

	/** 알림 1건 적재(수신자별). 같은 수신자+유형+eventKey 중복 시 무시(멱등 — ON DUPLICATE KEY no-op). */
	int insertIfAbsent(OperatorNotificationVO vo);

	/**
	 * 운영자 여부 검증 — JWT 만 신뢰하지 않고 DB 기준({@code USER_TYPE_CD='OPERATOR' AND DEL_YN='N'}).
	 * MEMBER/PARTNER/미존재는 false.
	 */
	boolean existsOperator(@Param("userSn") long userSn);

	/** 본인 알림 목록(미삭제·최근 90일·최신순·페이징). */
	List<OperatorNotificationVO> selectByReceiver(@Param("userSn") long userSn,
			@Param("size") int size, @Param("offset") int offset);

	/** 본인 알림 총건수(미삭제·최근 90일). */
	long countByReceiver(@Param("userSn") long userSn);

	/** 본인 미읽음 수(미삭제·최근 90일). */
	long countUnread(@Param("userSn") long userSn);

	/** 본인 알림 1건 읽음 처리(멱등 — 본인·미읽음·미삭제일 때만 1건, 그 외 0). */
	int markRead(@Param("userSn") long userSn, @Param("notiSn") long notiSn);

	/** 본인 미읽음 전체 읽음 처리. */
	int markAllRead(@Param("userSn") long userSn);

	/** 보관기간(90일) 경과분 하드 삭제 — ③a NotificationRetentionJob 스케줄러에서 사용. */
	int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
