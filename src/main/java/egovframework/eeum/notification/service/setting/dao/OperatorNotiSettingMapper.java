package egovframework.eeum.notification.service.setting.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.notification.vo.OperatorNotiSettingVO;

/**
 * 운영자 알림 설정 매퍼 — XML: {@code egovframework/mapper/eeum/notification/OperatorNotiSettingMapper_mysql.xml}
 */
@Mapper
public interface OperatorNotiSettingMapper {

	/**
	 * 운영자 여부 검증 — JWT userTypeCd 만 신뢰하지 않고 DB 기준으로 확인.
	 * 조건: {@code USER_TYPE_CD='OPERATOR' AND DEL_YN='N'}. MEMBER/PARTNER/미존재는 false.
	 */
	boolean existsOperator(@Param("userSn") long userSn);

	/** 본인 설정 1행 조회(미삭제) — 없으면 null(서비스가 기본값 반환). */
	OperatorNotiSettingVO selectByUserSn(@Param("userSn") long userSn);

	/** 설정 upsert(전체 교체) — INSERT 후 존재 시 4개 토글 갱신. Y/N 문자로 전달. */
	int upsert(@Param("userSn") long userSn,
			@Param("rsvtNotiYn") String rsvtNotiYn,
			@Param("inqrNotiYn") String inqrNotiYn,
			@Param("stlmNotiYn") String stlmNotiYn,
			@Param("mktgNotiYn") String mktgNotiYn);
}
