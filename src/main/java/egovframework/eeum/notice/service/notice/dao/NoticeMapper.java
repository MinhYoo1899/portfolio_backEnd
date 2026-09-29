package egovframework.eeum.notice.service.notice.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.notice.vo.NoticeVO;

/**
 * 공지 매퍼 — NOTICE CRUD(soft delete) + 종료 전이 + 예약공지 자동승격(cron).
 * 상태(NOTICE_STTS_CD)는 저장 라이프사이클 — 조회는 저장값 그대로.
 */
@Mapper
public interface NoticeMapper {

	/** 등록 — useGeneratedKeys 로 noticeSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertNotice(NoticeVO vo);

	/** 목록(미삭제) — 상태/대상/알림/제목 필터 + LIMIT/OFFSET, SN DESC. */
	List<NoticeVO> selectNoticeList(@Param("status") String status, @Param("target") String target,
			@Param("notifySend") String notifySend, @Param("keyword") String keyword,
			@Param("offset") int offset, @Param("limit") int limit);

	/** 목록 총건수(미삭제, 동일 필터). */
	long countNotice(@Param("status") String status, @Param("target") String target,
			@Param("notifySend") String notifySend, @Param("keyword") String keyword);

	/** 단건(미삭제). 없으면 null. */
	NoticeVO selectNoticeById(@Param("noticeSn") long noticeSn);

	/** 편집(제목/내용/대상/발송시점/예약일시) — 상태/발행일/알림여부 보존. 영향행 0 = 없음. */
	int updateNotice(NoticeVO vo);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteNotice(@Param("noticeSn") long noticeSn, @Param("mdfrSn") long mdfrSn);

	/** 수동 종료 — 게시중(PUBLISHED_ONGOING)만 CLOSED 로. 영향행 0 = 게시중 아님/없음. */
	int closeNotice(@Param("noticeSn") long noticeSn, @Param("mdfrSn") long mdfrSn);

	/** 예약공지 자동승격(cron) — SCHEDULED 중 예약시각 도래분을 PUBLISHED_ONGOING+발행일+알림'Y' 로. 승격 건수 반환. */
	int promoteScheduled();
}
