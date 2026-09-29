package egovframework.eeum.house.service.amenity.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.house.vo.AmenityVO;

/**
 * 편의시설 매퍼 — XML: {@code egovframework/mapper/eeum/house/AmenityMapper_mysql.xml}
 */
@Mapper
public interface AmenityMapper {

	/** 편의시설 생성(운영자 수기) — amenitySn generated key 수신. CUSTOM_YN='N'. APPLY_SCOPE_CD는 요청값(VO). */
	void insertAmenity(AmenityVO amenity);

	/**
	 * 커스텀(직접입력) 편의시설 insert-or-get(원자적) — CUSTOM_YN='Y', 즉시 활성(승인 게이트 없음).
	 * 동일 활성명이 이미 있으면 {@code ON DUPLICATE KEY UPDATE ... LAST_INSERT_ID} 로 그 행의 amenitySn 을 받는다(경합 안전).
	 */
	void insertCustomAmenity(AmenityVO amenity);

	/** 검증용 행 잠금(FOR UPDATE). */
	AmenityVO selectAmenityForUpdate(@Param("amenitySn") long amenitySn);

	/**
	 * 목록(분류·정렬순). {@code onlyActive=true} 면 사용중(USE_YN='Y')만(선택용 카탈로그),
	 * false 면 비활성 포함 전체(운영자 관리용). 반려/삭제(DEL_YN='Y')는 항상 제외.
	 */
	List<AmenityVO> selectAmenities(@Param("onlyActive") boolean onlyActive);

	/**
	 * 요청 ID 중 "활성(USE_YN='Y', 미삭제) + 적용범위 일치" 개수 — 매핑 검증용.
	 * {@code scopes} 에 매핑 대상 범위(객실=ROOM,BOTH / 고택=HOUSE,BOTH)를 넘긴다. 승인 게이트 없음.
	 */
	int countActiveScopedByIds(@Param("ids") List<Long> ids, @Param("scopes") List<String> scopes);

	/** 수정 — 미삭제일 때만. */
	int updateAmenity(@Param("amenitySn") long amenitySn, @Param("amenityCtgryCd") String amenityCtgryCd,
			@Param("applyScopeCd") String applyScopeCd, @Param("amenityNm") String amenityNm,
			@Param("iconKey") String iconKey, @Param("sortSeq") int sortSeq, @Param("mdfrSn") long mdfrSn);

	/** 사용여부(노출) 변경 — 미삭제일 때만. */
	int updateAmenityActive(@Param("amenitySn") long amenitySn, @Param("useYn") String useYn,
			@Param("mdfrSn") long mdfrSn);

	/** 활성 분류코드 존재 검증(공통코드 AMENITY_CTGRY — 임의 분류 저장 차단). */
	int countActiveCategory(@Param("ctgryCd") String ctgryCd);
}
