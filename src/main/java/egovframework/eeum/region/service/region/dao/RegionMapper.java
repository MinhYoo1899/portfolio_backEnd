package egovframework.eeum.region.service.region.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.region.vo.RegionVO;

/**
 * 지역 매퍼 — XML: {@code egovframework/mapper/eeum/region/RegionMapper_mysql.xml}
 *
 * <p>조회 전용(읽기). 시도/시군구 마스터는 운영자·공개 드롭다운의 안정 코드 소스.
 */
@Mapper
public interface RegionMapper {

	/** 시도(DEPTH=1) 목록 — 사용중(USE_YN='Y')·미삭제(DEL_YN='N'), 정렬순서 순(17개). */
	List<RegionVO> selectSidoRegions();

	/** 시도코드 유효성 — DEPTH=1·사용중·미삭제면 1, 아니면 0. */
	int countSido(@Param("regionCd") String regionCd);

	/** 시군구가 지정 시도 소속인지 — DEPTH=2·UPPER=sidoCd·사용중·미삭제면 1, 아니면 0. */
	int countSigunguInSido(@Param("sigunguCd") String sigunguCd, @Param("sidoCd") String sidoCd);
}
