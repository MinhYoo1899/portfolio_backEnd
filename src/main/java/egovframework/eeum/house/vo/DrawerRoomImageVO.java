package egovframework.eeum.house.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상세 drawer 객실 이미지 VO(roomSn IN 배치) — 첨부일련번호 + 대표여부 + 정렬순서.
 *
 * <p>응답 URL 은 {@code /files/download?atchFileSn=} 로 조립. 대표(RPRS_YN='Y') 포함 활성 이미지 전체.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawerRoomImageVO {

	private Long roomSn;
	private Long atchFileSn;
	private String rprsYn;
	private Integer sortSeq;
}
