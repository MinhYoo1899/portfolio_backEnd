package egovframework.eeum.house.service.amenity;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.AmenityActiveChangeRequest;
import egovframework.eeum.house.web.dto.AmenityInsertRequest;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.AmenityUpdateRequest;

/**
 * 편의시설 카탈로그 서비스.
 *
 * <p>선택용 조회(사용중 항목)는 인증 사용자 누구나, 카탈로그 관리(추가/수정/사용여부)는 운영자급(AMENITY_MNG).
 * 승인 게이트 없음 — 운영자/파트너가 추가하면 즉시 활성 카탈로그 항목이 된다.
 */
public interface AmenityService {

	/** 선택용 목록 — 사용중(USE_YN='Y') 항목만(판매자 고택 등록/수정 화면). */
	List<AmenityResponse> listForSelection();

	/** 관리용 목록 — 비활성 포함 전체(운영자급). */
	List<AmenityResponse> listAll();

	/** 편의시설 추가(운영자급). */
	AmenityResponse insert(AmenityInsertRequest request, AuthUser operator);

	/** 편의시설 수정(운영자급). */
	void update(AmenityUpdateRequest request, AuthUser operator);

	/** 사용여부(노출) 변경(운영자급). */
	void changeActive(AmenityActiveChangeRequest request, AuthUser operator);
}
