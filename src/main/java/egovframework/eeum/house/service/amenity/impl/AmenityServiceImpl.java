package egovframework.eeum.house.service.amenity.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.AmenityErrorCode;
import egovframework.eeum.house.service.amenity.AmenityService;
import egovframework.eeum.house.service.amenity.dao.AmenityMapper;
import egovframework.eeum.house.vo.AmenityVO;
import egovframework.eeum.house.web.dto.AmenityActiveChangeRequest;
import egovframework.eeum.house.web.dto.AmenityInsertRequest;
import egovframework.eeum.house.web.dto.AmenityResponse;
import egovframework.eeum.house.web.dto.AmenityUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 편의시설 카탈로그 서비스 구현 — 운영자급 관리, 분류 유효성(공통코드) 검증, 활성 유니크 중복 처리.
 * 승인 게이트 없음 — 추가 시 즉시 활성 카탈로그 항목.
 */
@Service
@RequiredArgsConstructor
public class AmenityServiceImpl implements AmenityService {

	private final AmenityMapper amenityMapper;

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listForSelection() {
		// 선택용 카탈로그 — 활성(USE_YN='Y')만.
		return amenityMapper.selectAmenities(true).stream().map(AmenityResponse::from).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> listAll() {
		// 운영자 관리용 — 비활성 포함. 삭제(DEL_YN='Y')는 제외.
		return amenityMapper.selectAmenities(false).stream().map(AmenityResponse::from).toList();
	}

	@Override
	@Transactional
	@Audit(actn = "AMENITY_INSERT", trgtType = "AMENITY_MST", trgtId = "#result.amenitySn()")
	public AmenityResponse insert(AmenityInsertRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		validateCategory(request.amenityCtgryCd());
		AmenityVO vo = AmenityVO.builder()
				.amenityCtgryCd(request.amenityCtgryCd())
				.applyScopeCd(request.applyScopeCd())
				.amenityNm(request.amenityNm())
				.iconKey(request.iconKey())
				.sortSeq(request.sortSeqOrDefault())
				.rgtrSn(op.userSn())
				.build();
		try {
			amenityMapper.insertAmenity(vo); // USE_YN='Y', DEL_YN='N', CUSTOM_YN='N' DB 기본값
		} catch (DuplicateKeyException e) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NAME_DUPLICATE,
					AmenityErrorCode.AMENITY_NAME_DUPLICATE.defaultMessage(), e);
		}
		return AmenityResponse.from(vo);
	}

	@Override
	@Transactional
	@Audit(actn = "AMENITY_UPDATE", trgtType = "AMENITY_MST", trgtId = "#request.amenitySn()")
	public void update(AmenityUpdateRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		requireActive(amenityMapper.selectAmenityForUpdate(request.amenitySn()));
		validateCategory(request.amenityCtgryCd());
		// 명칭 변경이 활성 유니크(UK_AMENITY_NM)와 충돌하면 DuplicateKey → NAME_DUPLICATE 로 번역(codex)
		final int updated;
		try {
			updated = amenityMapper.updateAmenity(request.amenitySn(), request.amenityCtgryCd(), request.applyScopeCd(),
					request.amenityNm(), request.iconKey(), request.sortSeqOrDefault(), op.userSn());
		} catch (DuplicateKeyException e) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NAME_DUPLICATE,
					AmenityErrorCode.AMENITY_NAME_DUPLICATE.defaultMessage(), e);
		}
		if (updated == 0) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	@Audit(actn = "AMENITY_ACTIVE_CHANGE", trgtType = "AMENITY_MST", trgtId = "#request.amenitySn()")
	public void changeActive(AmenityActiveChangeRequest request, AuthUser operator) {
		AuthUser op = requireUser(operator);
		requireActive(amenityMapper.selectAmenityForUpdate(request.amenitySn()));
		if (amenityMapper.updateAmenityActive(request.amenitySn(), request.useYn(), op.userSn()) == 0) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------------

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	private static void requireActive(AmenityVO vo) {
		if (vo == null || "Y".equals(vo.getDelYn())) {
			throw new BusinessException(AmenityErrorCode.AMENITY_NOT_FOUND);
		}
	}

	/** 분류코드 유효성(공통코드 AMENITY_CTGRY 존재) — 임의 분류 저장 차단. */
	private void validateCategory(String ctgryCd) {
		if (amenityMapper.countActiveCategory(ctgryCd) == 0) {
			throw new BusinessException(AmenityErrorCode.AMENITY_INVALID_CATEGORY);
		}
	}
}
