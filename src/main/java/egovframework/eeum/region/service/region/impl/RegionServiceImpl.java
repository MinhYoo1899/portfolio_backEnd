package egovframework.eeum.region.service.region.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.region.service.region.RegionService;
import egovframework.eeum.region.service.region.dao.RegionMapper;
import egovframework.eeum.region.vo.RegionVO;
import lombok.RequiredArgsConstructor;

/**
 * 지역 서비스 구현 — 조회 전용(읽기). 캐싱은 추후 트래픽 증가 시(마스터라 변경 드묾).
 */
@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

	private final RegionMapper regionMapper;

	@Override
	@Transactional(readOnly = true)
	public List<RegionVO> sidoRegions() {
		return regionMapper.selectSidoRegions();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isValidHouseRegion(String sidoRegionCd, String sigunguRegionCd) {
		if (sidoRegionCd == null && sigunguRegionCd == null) {
			return true; // DRAFT 등 미지정 허용
		}
		if (sigunguRegionCd != null && sidoRegionCd == null) {
			return false; // 시군구만 지정 불가(시도 필수)
		}
		if (sidoRegionCd != null && regionMapper.countSido(sidoRegionCd) == 0) {
			return false; // 미존재/비활성 시도
		}
		if (sigunguRegionCd != null && regionMapper.countSigunguInSido(sigunguRegionCd, sidoRegionCd) == 0) {
			return false; // 시군구가 그 시도 소속 아님/비활성
		}
		return true;
	}
}
