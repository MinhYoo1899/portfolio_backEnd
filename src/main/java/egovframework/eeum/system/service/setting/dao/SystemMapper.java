package egovframework.eeum.system.service.setting.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.system.vo.SystemIntegrationVO;
import egovframework.eeum.system.vo.SystemSettingVO;

/**
 * 시스템 설정/외부연동 매퍼 — 단일행 설정 조회/저장 + 연동 목록.
 */
@Mapper
public interface SystemMapper {

	/** 시스템 설정(단일행 SN=1) 조회. 없으면 null. */
	SystemSettingVO selectSetting();

	/** 시스템 설정 전체 저장(단일행 SN=1). 영향행 0 = 없음. */
	int updateSetting(@Param("serviceNm") String serviceNm, @Param("csCenterTelno") String csCenterTelno,
			@Param("oprtEmlAddr") String oprtEmlAddr, @Param("faxNo") String faxNo,
			@Param("autoLogoutMin") int autoLogoutMin, @Param("pswdChgCycleCd") String pswdChgCycleCd,
			@Param("mdfrSn") long mdfrSn);

	/** 외부연동 목록(SORT_ORDER 순). */
	List<SystemIntegrationVO> selectIntegrations();
}
