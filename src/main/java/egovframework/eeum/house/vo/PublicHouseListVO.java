package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 손님 공개 고택 목록 조회 결과(읽기 전용 프로젝션).
 *
 * <p>지역명은 REGION 마스터 조인, 대표이미지는 HOUSE_IMAGE(RPRS_YN='Y'), 인원범위는 ACTIVE 객실 집계.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicHouseListVO {

	private Long houseSn;
	private String houseNm;
	private String sidoNm;
	private String sigunguNm;
	private Long rprsAtchFileSn; // 대표이미지 첨부파일 일련번호(없으면 null)
	private Integer bscPrsnCnt;  // ACTIVE 객실 최소 기준인원
	private Integer maxPrsnCnt;  // ACTIVE 객실 최대 최대인원
}
