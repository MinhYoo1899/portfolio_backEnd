package egovframework.eeum.house.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 편의시설 VO — AMENITY_MST 매핑.
 *
 * <p>전 판매자가 고택 등록 시 선택하는 편의시설 항목(주차/와이파이/넷플릭스 등) 마스터.
 * 운영자급(AMENITY_MNG)이 동적으로 추가/수정/비활성한다. 전역 기준데이터.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmenityVO {

	/** 편의시설일련번호(PK). */
	private Long amenitySn;

	/** 편의시설분류코드(공통코드 AMENITY_CTGRY). */
	private String amenityCtgryCd;

	/** 적용범위(HOUSE/ROOM/BOTH). */
	private String applyScopeCd;

	/** 편의시설명. */
	private String amenityNm;

	/** 안정 문자열 키(seed/아이콘/다국어용. 커스텀은 null). */
	private String amenityKey;

	/** 커스텀(운영자/파트너 직접입력) 여부(Y/N). */
	private String customYn;

	/** 커스텀 직접입력 최초 요청자(감사용). */
	private Long requestUserSn;

	/** 아이콘키(프론트 아이콘 매핑용). */
	private String iconKey;

	/** 정렬순서. */
	private Integer sortSeq;

	/** 사용여부(N=노출중지). */
	private String useYn;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;
}
