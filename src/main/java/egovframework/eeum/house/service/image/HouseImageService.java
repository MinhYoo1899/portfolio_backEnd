package egovframework.eeum.house.service.image;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.HouseImageDeleteRequest;
import egovframework.eeum.house.web.dto.HouseImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.HouseImageResponse;

/**
 * 고택 이미지 서비스(판매자) — 본인 소유 고택의 공개 이미지 추가/조회/삭제/대표지정.
 *
 * <p>업로드는 기존 공개 이미지 파이프라인(FileService) 재사용(검증·SFTP·쿼터), 정렬·대표는 HOUSE_IMAGE가 책임.
 */
public interface HouseImageService {

	/** 이미지 추가(업로드 즉시 연결) — 첫 이미지는 자동 대표. */
	HouseImageResponse addImage(long houseSn, MultipartFile file, AuthUser partner);

	/** 고택 이미지 목록(정렬순). */
	List<HouseImageResponse> listByHouse(long houseSn, AuthUser partner);

	/** 이미지 삭제 — 대표 삭제 시 가장 앞 이미지로 자동 승격. */
	void delete(HouseImageDeleteRequest request, AuthUser partner);

	/** 대표 이미지 지정(기존 대표 해제 후 교체). */
	void setRepresentative(HouseImageRepresentativeRequest request, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner 는 대상 고택에서 서버 해석, 행위자=운영자 =====

	/** 운영자 대행 이미지 추가. */
	HouseImageResponse addImageAsOperator(long houseSn, MultipartFile file, AuthUser operator);

	/** 운영자 대행 이미지 목록. */
	List<HouseImageResponse> listByHouseAsOperator(long houseSn, AuthUser operator);

	/** 운영자 대행 이미지 삭제. */
	void deleteAsOperator(HouseImageDeleteRequest request, AuthUser operator);

	/** 운영자 대행 대표 이미지 지정. */
	void setRepresentativeAsOperator(HouseImageRepresentativeRequest request, AuthUser operator);
}
