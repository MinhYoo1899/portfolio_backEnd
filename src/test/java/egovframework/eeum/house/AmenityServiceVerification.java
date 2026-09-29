package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.AmenityErrorCode;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.service.amenity.impl.AmenityServiceImpl;
import egovframework.eeum.house.vo.AmenityVO;
import egovframework.eeum.house.web.dto.AmenityActiveChangeRequest;
import egovframework.eeum.house.web.dto.AmenityInsertRequest;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.AmenityUpdateRequest;

/**
 * 편의시설 카탈로그 서비스 검증 — 분류 유효성/중복명/미존재/사용여부/적용범위(DB 없이 mock). 승인 게이트 없음.
 */
class AmenityServiceVerification {

	private final AmenityMapper mapper = mock(AmenityMapper.class);
	private final AmenityServiceImpl service = new AmenityServiceImpl(mapper);

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("AMENITY_MNG"));
	}

	private static AmenityInsertRequest insertReq() {
		return new AmenityInsertRequest("ROOM_FEATURE", "ROOM", "무료 와이파이", "wifi", 1);
	}

	@Test
	@DisplayName("선택용 목록은 사용중 항목만(onlyActive=true)")
	void listForSelectionActiveOnly() {
		when(mapper.selectAmenities(true)).thenReturn(List.of());
		service.listForSelection();
		verify(mapper).selectAmenities(true);
	}

	@Test
	@DisplayName("관리용 목록은 비활성 포함 전체(onlyActive=false)")
	void listAllIncludesInactive() {
		when(mapper.selectAmenities(false)).thenReturn(List.of());
		service.listAll();
		verify(mapper).selectAmenities(false);
	}

	@Test
	@DisplayName("등록 — 유효 분류면 생성")
	void insertSuccess() {
		when(mapper.countActiveCategory("ROOM_FEATURE")).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, AmenityVO.class).setAmenitySn(9L);
			return null;
		}).when(mapper).insertAmenity(any(AmenityVO.class));
		AmenityResponse res = service.insert(insertReq(), operator(2));
		assertEquals(9L, res.amenitySn());
		assertEquals("ROOM", res.applyScopeCd());
		verify(mapper).insertAmenity(any(AmenityVO.class));
	}

	@Test
	@DisplayName("등록 — 유효하지 않은 분류면 400")
	void insertInvalidCategory() {
		when(mapper.countActiveCategory("ROOM_FEATURE")).thenReturn(0);
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(insertReq(), operator(2)));
		assertEquals(AmenityErrorCode.AMENITY_INVALID_CATEGORY, e.getErrorCode());
		verify(mapper, never()).insertAmenity(any());
	}

	@Test
	@DisplayName("등록 — 편의시설명 중복은 409")
	void insertDuplicateName() {
		when(mapper.countActiveCategory("ROOM_FEATURE")).thenReturn(1);
		doAnswer(inv -> {
			throw new DuplicateKeyException("dup");
		}).when(mapper).insertAmenity(any(AmenityVO.class));
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(insertReq(), operator(2)));
		assertEquals(AmenityErrorCode.AMENITY_NAME_DUPLICATE, e.getErrorCode());
	}

	@Test
	@DisplayName("수정 — 대상 없으면 404")
	void updateNotFound() {
		when(mapper.selectAmenityForUpdate(9L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new AmenityUpdateRequest(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1), operator(2)));
		assertEquals(AmenityErrorCode.AMENITY_NOT_FOUND, e.getErrorCode());
		verify(mapper, never()).updateAmenity(anyLong(), anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("수정 — 활성 + 유효 분류면 성공(적용범위 포함)")
	void updateSuccess() {
		when(mapper.selectAmenityForUpdate(9L))
				.thenReturn(AmenityVO.builder().amenitySn(9L).delYn("N").build());
		when(mapper.countActiveCategory("ROOM_FEATURE")).thenReturn(1);
		when(mapper.updateAmenity(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1, 2L)).thenReturn(1);
		service.update(new AmenityUpdateRequest(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1), operator(2));
		verify(mapper).updateAmenity(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1, 2L);
	}

	@Test
	@DisplayName("수정 — 명칭 중복(활성유니크 충돌)은 409로 번역")
	void updateDuplicateName() {
		when(mapper.selectAmenityForUpdate(9L))
				.thenReturn(AmenityVO.builder().amenitySn(9L).delYn("N").build());
		when(mapper.countActiveCategory("ROOM_FEATURE")).thenReturn(1);
		when(mapper.updateAmenity(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1, 2L))
				.thenThrow(new DuplicateKeyException("dup"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new AmenityUpdateRequest(9L, "ROOM_FEATURE", "ROOM", "수정", "wifi", 1), operator(2)));
		assertEquals(AmenityErrorCode.AMENITY_NAME_DUPLICATE, e.getErrorCode());
	}

	@Test
	@DisplayName("사용여부 변경 — 활성 대상이면 성공")
	void changeActiveSuccess() {
		when(mapper.selectAmenityForUpdate(9L))
				.thenReturn(AmenityVO.builder().amenitySn(9L).delYn("N").build());
		when(mapper.updateAmenityActive(9L, "N", 2L)).thenReturn(1);
		service.changeActive(new AmenityActiveChangeRequest(9L, "N"), operator(2));
		verify(mapper).updateAmenityActive(9L, "N", 2L);
	}
}
