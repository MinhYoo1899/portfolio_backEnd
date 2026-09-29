package egovframework.eeum.house.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 상품(고택) 목록 필터 옵션 응답 — OPS 상품관리 드롭다운/검색 계약(프론트 수동 하드코딩 제거용).
 *
 * <p>백엔드 실제값만 내려준다: 상태는 실제 {@code HOUSE_STTS_CD}(검수요청/검수중 없음), 지역은 REGION 시도 마스터.
 * "전체" 옵션은 백엔드가 내려주지 않는다 — 프론트가 prepend 한다(각 목록은 실제 선택 가능한 값만).
 *
 * @param statuses    상태 필터 옵션(노출 순서). 없는 상태(REVIEW 등)는 포함하지 않음
 * @param regions     지역(시도) 옵션 — value=시도코드(2자리), label=표시명. 정렬순서 보장. 목록 지역필터 지원(unsupported.regionFilter=false). 미매핑 데이터 건수는 regionUnmappedCount 로 별도 제공
 * @param search      검색 메타(실제 검색 범위와 placeholder 동기화)
 * @param unsupported 화면엔 있으나 백엔드가 지원하지 않는 항목 플래그(프론트 fake 노출 방지)
 */
@Schema(description = "운영자 상품 목록 필터 옵션")
public record OperatorHouseFilterOptionsResponse(
		@Schema(description = "상태 필터 옵션(노출 순서, 실제 HOUSE_STTS_CD만)") List<StatusOption> statuses,
		@Schema(description = "지역(시도) 필터 옵션") List<RegionOption> regions,
		@Schema(description = "검색 메타") SearchMeta search,
		@Schema(description = "미지원(기능 지원 여부) 플래그") Unsupported unsupported,
		@Schema(description = "지역 미매핑 고택 수(비삭제·SIDO_REGION_CD NULL) — 지역필터 시 제외되는 건수(UX 안내용). 기능 지원과 무관.",
				example = "3") long regionUnmappedCount) {

	/**
	 * 상태/지역 옵션 + 지역 미매핑 건수를 받아 검색 메타·미지원 플래그를 합성한다.
	 * unsupported.regionFilter=false(필터 WHERE 구현됨 → 기능 지원). 데이터 미매핑은 {@code regionUnmappedCount}로 분리 제공
	 * (기능 지원 여부와 데이터 완비 여부를 혼동하지 않게 — NULL 한 건으로 필터 기능이 영구 비활성되지 않도록).
	 */
	public static OperatorHouseFilterOptionsResponse of(List<StatusOption> statuses, List<RegionOption> regions,
			long regionUnmappedCount) {
		return new OperatorHouseFilterOptionsResponse(
				statuses,
				regions,
				new SearchMeta(List.of("houseName", "partnerName"), "고택명, 파트너명으로 검색"),
				new Unsupported(false, false, false),
				regionUnmappedCount);
	}

	/**
	 * @param value   실제 상태코드(분기 기준)
	 * @param label   표시용 라벨
	 * @param enabled 필터로 선택 가능 여부(현재 전 상태 true)
	 */
	@Schema(description = "상태 필터 옵션 항목")
	public record StatusOption(
			@Schema(description = "상태코드(값)", example = "ACTIVE") String value,
			@Schema(description = "표시 라벨", example = "게재중") String label,
			@Schema(description = "선택 가능 여부", example = "true") boolean enabled) {
	}

	/**
	 * @param value 시도코드(REGION_CD 2자리)
	 * @param label 시도 표시명(짧은명 우선)
	 */
	@Schema(description = "지역(시도) 필터 옵션 항목")
	public record RegionOption(
			@Schema(description = "시도코드(값)", example = "11") String value,
			@Schema(description = "표시명", example = "서울") String label) {
	}

	/**
	 * @param keywordTargets 실제 검색 대상 필드(houseName, partnerName …)
	 * @param placeholder    검색창 placeholder(실제 검색 범위와 일치)
	 */
	@Schema(description = "검색 메타")
	public record SearchMeta(
			@Schema(description = "검색 대상 필드") List<String> keywordTargets,
			@Schema(description = "검색창 placeholder", example = "고택명, 파트너명으로 검색") String placeholder) {
	}

	/**
	 * 화면엔 있으나 백엔드 미지원 — 프론트 fake 노출/오작동 방지.
	 *
	 * @param reviewStatus   검수요청 상태 미지원 여부. 상태머신 도입됨(2026-07-06) → false(지원)
	 * @param partnerKeyword 파트너명 검색 미지원 여부(현재 지원하므로 false)
	 * @param regionFilter   지역 목록필터 <b>기능</b> 미지원 여부. 필터 WHERE 구현됨 → false(지원). 데이터 완비 여부는 별개(regionUnmappedCount)
	 */
	@Schema(description = "미지원(기능 지원 여부) 플래그")
	public record Unsupported(
			@Schema(description = "검수요청 상태 미지원(도입됨=false)", example = "false") boolean reviewStatus,
			@Schema(description = "파트너명 검색 미지원(현재 지원=false)", example = "false") boolean partnerKeyword,
			@Schema(description = "지역 목록필터 기능 미지원(구현됨=false)", example = "false") boolean regionFilter) {
	}
}
