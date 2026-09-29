package egovframework.eeum.house.service.publichouse.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.amenity.dao.HouseAmenityMapper;
import egovframework.eeum.house.service.availability.AvailabilityService;
import egovframework.eeum.house.service.house.dao.HousePayMethodMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.service.publichouse.PublicHouseService;
import egovframework.eeum.house.service.publichouse.dao.PublicHouseMapper;
import egovframework.eeum.house.vo.PublicHouseDetailVO;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.AvailabilitySearchRequest;
import egovframework.eeum.house.web.dto.PublicAvailabilityResponse;
import egovframework.eeum.house.web.dto.PublicHouseDetailResponse;
import egovframework.eeum.house.web.dto.PublicHouseImageItem;
import egovframework.eeum.house.web.dto.PublicHouseListItem;
import egovframework.eeum.house.web.dto.PublicHouseSearchRequest;
import egovframework.eeum.house.web.dto.PublicRoomSummary;
import lombok.RequiredArgsConstructor;

/**
 * 손님 공개 고택 브라우즈 구현 — 목록/상세/가용검색. 공개 가시성(ACTIVE 고택+승인·활성 파트너) 밖은 전부 404 은닉.
 *
 * <p>상세는 공개 전용 SELECT(비공개 = 0행)로 읽고, 이미지/편의시설/결제수단은 기존 표출용 매퍼를 재사용한다
 * (표출용 조회는 house 도메인 내부 — 도메인 간 DAO 접근 아님). 가용검색은 {@link AvailabilityService} 에 판정을 위임하고
 * 이 계층은 공개 채널 규칙(과거일 400, 비공개 404, 최저가)만 얹는다.
 */
@Service
@RequiredArgsConstructor
public class PublicHouseServiceImpl implements PublicHouseService {

	/** 과거 체크인 판정 기준 타임존(KST 영업일) — 예약 생성 validateRange 와 동일 기준. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PublicHouseMapper publicHouseMapper;
	private final HouseImageMapper houseImageMapper;
	private final HouseAmenityMapper houseAmenityMapper;
	private final HousePayMethodMapper housePayMethodMapper;
	private final AvailabilityService availabilityService;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<PublicHouseListItem> list(PublicHouseSearchRequest request) {
		String keyword = escapeLike(trimToNull(request.keyword()));
		int page = request.pageOrDefault();
		int pageSize = request.pageSizeOrDefault();
		long total = publicHouseMapper.countPublicHouses(keyword);
		List<PublicHouseListItem> items = publicHouseMapper
				.selectPublicHouseListOffset(keyword, pageSize, request.offset()).stream()
				.map(PublicHouseListItem::from)
				.toList();
		return PageResponse.of(items, total, page, pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public PublicHouseDetailResponse detail(long houseSn) {
		// 공개 가시성 통과 행만 — 미존재/DRAFT/HIDDEN/SUSPENDED/삭제/미승인·정지 파트너 전부 동일 404(존재 은닉)
		PublicHouseDetailVO house = publicHouseMapper.selectPublicHouseDetail(houseSn);
		if (house == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		List<PublicHouseImageItem> images = houseImageMapper.selectActiveImagesByHouse(houseSn).stream()
				.map(PublicHouseImageItem::from)
				.toList();
		List<AmenityResponse> amenities = houseAmenityMapper.selectActiveAmenitiesByHouse(houseSn).stream()
				.map(AmenityResponse::from)
				.toList();
		List<String> payMethods = housePayMethodMapper.selectActiveCodesByHouse(houseSn);
		List<PublicRoomSummary> rooms = publicHouseMapper.selectPublicRoomsByHouse(houseSn).stream()
				.map(PublicRoomSummary::from)
				.toList();
		return PublicHouseDetailResponse.from(house, images, amenities, payMethods, rooms);
	}

	@Override
	@Transactional(readOnly = true)
	public PublicAvailabilityResponse availability(AvailabilitySearchRequest request) {
		// 1. KST 과거 체크인 차단(400) — 예약 생성과 동일 기준(오늘 체크인은 허용). 기존 보호 /availability 는 무변경
		if (request.checkInDate().isBefore(LocalDate.now(KST))) {
			throw new BusinessException(RoomErrorCode.CALENDAR_INVALID_RANGE, "체크인일은 오늘 이후여야 합니다.");
		}
		// 2. 비공개/미존재 고택 은닉(404) — 목록/상세와 동일 가시성(기존 /availability 의 200+빈배열과 달리 공개 계약은 404)
		if (publicHouseMapper.countPublicVisibleHouse(request.houseSn()) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 3. 가용성 판정 위임(기간검증·객실별 가용/사유/총액) + 공개 응답 래핑(최저가)
		return PublicAvailabilityResponse.from(availabilityService.search(request));
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	/** LIKE 와일드카드(%, _, \) 이스케이프 — ESCAPE '\' 와 함께 사용. */
	private static String escapeLike(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
