package egovframework.eeum.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.region.service.region.dao.RegionMapper;
import egovframework.eeum.region.service.region.impl.RegionServiceImpl;
import egovframework.eeum.region.vo.RegionVO;

/**
 * 지역 서비스 검증 — 시도 목록 조회 위임(DB 없이 mock).
 */
class RegionServiceVerification {

	private final RegionMapper regionMapper = mock(RegionMapper.class);
	private final RegionServiceImpl service = new RegionServiceImpl(regionMapper);

	@Test
	@DisplayName("시도 목록 — 매퍼(selectSidoRegions) 결과를 그대로 반환")
	void sidoRegionsDelegates() {
		List<RegionVO> rows = List.of(
				RegionVO.builder().regionCd("11").regionNm("서울특별시").regionShortNm("서울").sortSeq(1).build(),
				RegionVO.builder().regionCd("47").regionNm("경상북도").regionShortNm("경북").sortSeq(15).build());
		when(regionMapper.selectSidoRegions()).thenReturn(rows);

		assertEquals(rows, service.sidoRegions());
		verify(regionMapper).selectSidoRegions();
	}

	@Test
	@DisplayName("지역 검증 — 둘 다 null(DRAFT)은 true, 시군구만 지정은 false")
	void validHouseRegion_nullAndSigunguOnly() {
		assertTrue(service.isValidHouseRegion(null, null));
		assertFalse(service.isValidHouseRegion(null, "47170"));
	}

	@Test
	@DisplayName("지역 검증 — 시도 유효/무효 + 시군구-시도 계층 일치/불일치")
	void validHouseRegion_hierarchy() {
		when(regionMapper.countSido("47")).thenReturn(1);
		when(regionMapper.countSido("99")).thenReturn(0);
		when(regionMapper.countSigunguInSido("47170", "47")).thenReturn(1);
		when(regionMapper.countSigunguInSido("11110", "47")).thenReturn(0);

		assertTrue(service.isValidHouseRegion("47", null));     // 유효 시도
		assertFalse(service.isValidHouseRegion("99", null));    // 미존재 시도
		assertTrue(service.isValidHouseRegion("47", "47170"));  // 시군구가 그 시도 소속
		assertFalse(service.isValidHouseRegion("47", "11110")); // 시군구가 타 시도 소속
	}
}
