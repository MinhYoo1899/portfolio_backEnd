package egovframework.eeum.house.service.image.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.HouseImageVO;

/**
 * 고택 이미지 매퍼 — XML: {@code egovframework/mapper/eeum/house/HouseImageMapper_mysql.xml}
 *
 * <p>소유권은 서비스가 부모 고택을 FOR UPDATE 로 선검증하므로, 변경계는 {@code houseSn} 범위로 격리한다.
 */
@Mapper
public interface HouseImageMapper {

	/** 이미지 행 생성 — houseImgSn generated key 수신. */
	void insertHouseImage(HouseImageVO image);

	/** 고택 이미지 목록(ATCH_FILE 조인 — URL/원본명). 정렬순. */
	List<HouseImageVO> selectActiveImagesByHouse(@Param("houseSn") long houseSn);

	/** 이미지 행 잠금(FOR UPDATE) — houseSn/atchFileSn/sortSeq/rprsYn/delYn. 소유권은 부모 고택 선잠금으로 검증. */
	HouseImageVO selectHouseImageForUpdate(@Param("houseImgSn") long houseImgSn);

	/** 고택 내 최대 정렬순서(없으면 0). */
	int maxSortSeqByHouse(@Param("houseSn") long houseSn);

	/** 고택 내 활성 이미지 수. */
	int countActiveImagesByHouse(@Param("houseSn") long houseSn);

	/** 고택 내 대표(RPRS_YN='Y') 활성 이미지 수 — 발행검증용(>=1 필요). */
	int countRepresentativeImagesByHouse(@Param("houseSn") long houseSn);

	/** 가장 앞(최소 정렬순서) 활성 이미지 일련번호 — 대표 자동 승격용(없으면 null). */
	Long selectFirstActiveImageSnByHouse(@Param("houseSn") long houseSn);

	/** 이미지 soft delete + 대표 해제 — 해당 고택의 활성 이미지일 때만. */
	int softDeleteHouseImage(@Param("houseImgSn") long houseImgSn, @Param("houseSn") long houseSn,
			@Param("mdfrSn") long mdfrSn);

	/** 고택의 현재 대표 전부 해제(RPRS_YN='N') — 대표 교체 전처리. */
	int clearRepresentativeByHouse(@Param("houseSn") long houseSn, @Param("mdfrSn") long mdfrSn);

	/** 대표 지정(RPRS_YN='Y') — 해당 고택의 활성 이미지일 때만. */
	int setRepresentative(@Param("houseImgSn") long houseImgSn, @Param("houseSn") long houseSn,
			@Param("mdfrSn") long mdfrSn);

	/** 고택의 활성 이미지가 가리키는 ATCH_FILE 일련번호 목록 — 삭제 cascade 시 첨부 정리용. */
	List<Long> selectActiveAtchFileSnsByHouse(@Param("houseSn") long houseSn);

	/** 고택 삭제 동반 — 활성 이미지 전부 soft delete. 소유권은 서비스가 선검증. */
	int softDeleteImagesByHouse(@Param("houseSn") long houseSn, @Param("mdfrSn") long mdfrSn);
}
