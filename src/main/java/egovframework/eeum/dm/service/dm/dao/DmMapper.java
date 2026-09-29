package egovframework.eeum.dm.service.dm.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.dm.vo.DmMessageVO;

/**
 * DM 매퍼 — DM_MESSAGE 생성/조회/soft delete + 예약 자동발송(cron). 상태는 저장 라이프사이클.
 */
@Mapper
public interface DmMapper {

	/** 등록 — useGeneratedKeys 로 dmMessageSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). */
	int insertDm(DmMessageVO vo);

	/** 목록(미삭제) — 상태/채널/제목 필터 + LIMIT/OFFSET, SN DESC. */
	List<DmMessageVO> selectDmList(@Param("status") String status, @Param("channelCd") String channelCd,
			@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

	/** 목록 총건수(미삭제, 동일 필터). */
	long countDm(@Param("status") String status, @Param("channelCd") String channelCd, @Param("keyword") String keyword);

	/** 단건(미삭제). 없으면 null. */
	DmMessageVO selectDmById(@Param("dmMessageSn") long dmMessageSn);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteDm(@Param("dmMessageSn") long dmMessageSn, @Param("mdfrSn") long mdfrSn);

	/** 예약 DM 자동발송(cron) — SCHEDULED 중 예약시각 도래분을 SENT+발송일 로(스텁). 발송 건수 반환. */
	int promoteScheduled();
}
