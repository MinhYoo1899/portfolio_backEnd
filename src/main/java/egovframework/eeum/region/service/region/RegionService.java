package egovframework.eeum.region.service.region;

import java.util.List;

import egovframework.eeum.region.vo.RegionVO;

/**
 * 지역 서비스 — 조회 전용(읽기). 시도/시군구 마스터를 드롭다운 등에 안정 코드로 제공.
 */
public interface RegionService {

	/** 시도(DEPTH=1) 목록 — 정렬순서 순(17개). */
	List<RegionVO> sidoRegions();

	/**
	 * 고택 지역코드 조합 유효성 — 저장 전 검증용.
	 * 둘 다 null=true(DRAFT 허용). sigungu만 있고 sido 없으면 false. sido 미존재/시군구가 그 시도 소속 아니면 false.
	 */
	boolean isValidHouseRegion(String sidoRegionCd, String sigunguRegionCd);
}
