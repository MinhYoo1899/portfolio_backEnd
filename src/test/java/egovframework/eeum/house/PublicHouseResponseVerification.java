package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.house.vo.PublicHouseDetailVO;
import egovframework.eeum.house.vo.PublicRoomSummaryVO;
import egovframework.eeum.house.web.dto.PublicHouseDetailResponse;
import egovframework.eeum.house.web.dto.PublicRoomSummary;

/**
 * 공개 고택 DTO 금지필드 회귀 — 공개 채널 응답에 내부 식별자/상태/주소상세/감사/재고설정이 되살아나는 것을 차단.
 * ({@code PartnerSelfResponseVerification} 패턴 — record component + 직렬화 양면으로 못박는다.)
 */
class PublicHouseResponseVerification {

	/** 공개 상세 응답에 절대 있으면 안 되는 컴포넌트/JSON 키. */
	private static final Set<String> FORBIDDEN_DETAIL = Set.of(
			"prtnrSn", "houseSttsCd", "zip", "daddr", "regDt", "mdfcnDt", "rgtrSn", "mdfrSn", "delYn",
			"picMblTelno", "prtnrEmlAddr", "userSn");

	private static final Set<String> FORBIDDEN_ROOM = Set.of(
			"roomSttsCd", "dfltStockQty", "regDt", "mdfcnDt", "rgtrSn", "mdfrSn", "delYn", "houseSn");

	private static Set<String> components(Class<?> record) {
		return Arrays.stream(record.getRecordComponents())
				.map(RecordComponent::getName).collect(Collectors.toSet());
	}

	@Test
	@DisplayName("상세 record component — 내부필드(prtnrSn/상태/zip/daddr/감사) 부재 + 공개 필수필드 존재")
	void detailResponse_hasNoInternalComponents() {
		Set<String> c = components(PublicHouseDetailResponse.class);
		FORBIDDEN_DETAIL.forEach(f -> assertFalse(c.contains(f), f + " 노출 금지(공개 채널)"));
		assertTrue(c.containsAll(Set.of("houseSn", "houseNm", "regionText", "addr", "rsvtCnfirmCd",
				"payMethods", "images", "amenities", "rooms", "seller")), "공개 계약 필수 필드 존재");
	}

	@Test
	@DisplayName("객실요약 record component — 상태코드/내부 재고설정(dfltStockQty)/감사 부재 + minStay 존재")
	void roomSummary_hasNoInternalComponents() {
		Set<String> c = components(PublicRoomSummary.class);
		FORBIDDEN_ROOM.forEach(f -> assertFalse(c.contains(f), f + " 노출 금지(공개 채널)"));
		assertTrue(c.containsAll(Set.of("roomSn", "roomNm", "bscPrsnCnt", "maxPrsnCnt", "minStayNgtCnt")));
	}

	@Test
	@DisplayName("직렬화 결과 — 금지 키가 JSON 에 등장하지 않는다(중첩 seller 포함)")
	void serialization_hasNoForbiddenKeys() throws Exception {
		PublicHouseDetailVO vo = PublicHouseDetailVO.builder()
				.houseSn(3L).houseNm("학봉종택").addr("경북 안동시").houseDc("소개")
				.sidoNm("경상북도").sigunguNm("안동시").rsvtCnfirmCd("AUTO")
				.conm("고즈넉한옥").rprsvNm("홍길동").brno("1234567890").mailOrderSalesNo("2026-경북안동-0001")
				.build();
		PublicRoomSummary room = PublicRoomSummary.from(PublicRoomSummaryVO.builder()
				.roomSn(11L).roomNm("안채").bscPrsnCnt(2).maxPrsnCnt(4).minStayNgtCnt(1).build());

		String json = new ObjectMapper().writeValueAsString(
				PublicHouseDetailResponse.from(vo, List.of(), List.of(), List.of("CARD"), List.of(room)));

		for (String forbidden : FORBIDDEN_DETAIL) {
			assertFalse(json.contains("\"" + forbidden + "\""), forbidden + " 키가 직렬화 결과에 노출되면 안 됨");
		}
		assertFalse(json.contains("\"dfltStockQty\""), "객실 내부 재고설정 미노출");
		assertTrue(json.contains("\"seller\"") && json.contains("\"mailOrderSalesNo\""), "판매자 표시정보는 노출");
	}
}
