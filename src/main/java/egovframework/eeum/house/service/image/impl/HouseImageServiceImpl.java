package egovframework.eeum.house.service.image.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.HouseImageService;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.vo.HouseImageVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseImageDeleteRequest;
import egovframework.eeum.house.web.dto.HouseImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.HouseImageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 고택 이미지 서비스 구현 — 공개 이미지 파이프라인 재사용 + 정렬/대표는 HOUSE_IMAGE가 책임.
 *
 * <p>추가는 ProfileImage 패턴: 업로드(트랜잭션 밖, FileService) → 별도 빈에서 행삽입 트랜잭션 → 실패 시 첨부 보상삭제.
 * 삭제·대표지정은 부모 고택 FOR UPDATE 아래에서 처리(락 순서 HOUSE→IMAGE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseImageServiceImpl implements HouseImageService {

	private static final String Y = "Y";
	private static final String TRGT_TYPE_HOUSE = "HOUSE";

	private final HouseImageMapper houseImageMapper;
	private final HouseMapper houseMapper;
	private final FileService fileService;
	private final PartnerService partnerService;
	private final HouseImageLinker houseImageLinker;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HousePublishGuard publishGuard;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Audit(actn = "HOUSE_IMAGE_ADD", trgtType = "HOUSE", trgtId = "#houseSn")
	public HouseImageResponse addImage(long houseSn, MultipartFile file, AuthUser partner) {
		return addImageCore(houseSn, requirePrtnrSn(partner), partner.userSn(), file);
	}

	@Override
	@Transactional(readOnly = true)
	public List<HouseImageResponse> listByHouse(long houseSn, AuthUser partner) {
		return listCore(houseSn, requirePrtnrSn(partner));
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_IMAGE_DELETE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void delete(HouseImageDeleteRequest request, AuthUser partner) {
		deleteCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_IMAGE_REPRESENTATIVE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setRepresentative(HouseImageRepresentativeRequest request, AuthUser partner) {
		setRepresentativeCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상고택 서버해석, 행위자=운영자 ============

	@Override
	@Audit(actn = "HOUSE_PROXY_IMAGE_ADD", trgtType = "HOUSE", trgtId = "#houseSn")
	public HouseImageResponse addImageAsOperator(long houseSn, MultipartFile file, AuthUser operator) {
		HouseImageResponse result = addImageCore(houseSn, proxyResolver.resolveOwnerForHouse(houseSn, operator),
				operator.userSn(), file);
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<HouseImageResponse> listByHouseAsOperator(long houseSn, AuthUser operator) {
		return listCore(houseSn, proxyResolver.resolveOwnerForHouse(houseSn, operator));
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_IMAGE_DELETE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void deleteAsOperator(HouseImageDeleteRequest request, AuthUser operator) {
		deleteCore(request, proxyResolver.resolveOwnerForHouse(request.houseSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "HOUSE_PROXY_IMAGE_REPRESENTATIVE", trgtType = "HOUSE", trgtId = "#request.houseSn()")
	public void setRepresentativeAsOperator(HouseImageRepresentativeRequest request, AuthUser operator) {
		setRepresentativeCore(request, proxyResolver.resolveOwnerForHouse(request.houseSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		reviewGuard.markDirtyIfUnderReview(request.houseSn(), operator.userSn());
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private HouseImageResponse addImageCore(long houseSn, long ownerPrtnrSn, long actorUserSn, MultipartFile file) {
		// 업로드 전 소유권 사전검증(미소유 고택에 업로드 낭비/노출 방지) — 행삽입 시 FOR UPDATE 로 재검증
		HouseVO pre = houseMapper.selectHouseDetail(houseSn);
		if (pre == null || !pre.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		// 업로드는 트랜잭션 밖(느린 SFTP) — 공개 이미지 검증(확장자/크기/매직/픽셀/쿼터)+저장
		FileUploadResponse stored = fileService.upload(file, actorUserSn, TRGT_TYPE_HOUSE, String.valueOf(houseSn));
		HouseImageVO created;
		try {
			// 보상삭제 catch 는 "행삽입 실패"에만 걸리도록 좁힌다(codex MUST) — 응답 생성은 try 밖
			created = houseImageLinker.link(houseSn, ownerPrtnrSn, stored.atchFileSn(), actorUserSn);
		} catch (RuntimeException e) {
			compensate(stored.atchFileSn(), actorUserSn); // 고아 첨부 정리(원 예외 보존)
			throw e;
		}
		created.setUrlAddr(stored.downloadUrl());
		created.setOrgnlFileNm(stored.orgnlFileNm());
		return HouseImageResponse.from(created);
	}

	/** 행삽입 실패 보상 — 보상 실패가 원 예외를 덮지 않도록 격리(실패분은 정리배치가 회수). */
	private void compensate(long atchFileSn, long actorUserSn) {
		try {
			fileService.softDeleteByAtchFileSn(atchFileSn, actorUserSn);
		} catch (RuntimeException ex) {
			log.warn("[HOUSE_IMAGE] 첨부 보상삭제 실패(정리배치 회수 대상) atchFileSn={}", atchFileSn, ex);
		}
	}

	private List<HouseImageResponse> listCore(long houseSn, long ownerPrtnrSn) {
		HouseVO house = houseMapper.selectHouseDetail(houseSn);
		if (house == null || !house.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		return houseImageMapper.selectActiveImagesByHouse(houseSn).stream()
				.map(HouseImageResponse::from).toList();
	}

	private void deleteCore(HouseImageDeleteRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedActiveHouse(request.houseSn(), ownerPrtnrSn);
		// 발행 후 표류 방지(비퇴행) — 변경 전 readiness 캡처(비ACTIVE면 null)
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), ownerPrtnrSn);
		HouseImageVO img = lockOwnedImage(request.houseImgSn(), request.houseSn());
		boolean wasRepresentative = Y.equals(img.getRprsYn());
		if (houseImageMapper.softDeleteHouseImage(request.houseImgSn(), request.houseSn(), actorUserSn) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_IMAGE_NOT_FOUND);
		}
		// 대표 삭제 시 가장 앞 이미지 자동 승격(남은 이미지 있으면)
		if (wasRepresentative) {
			Long first = houseImageMapper.selectFirstActiveImageSnByHouse(request.houseSn());
			if (first != null) {
				houseImageMapper.setRepresentative(first, request.houseSn(), actorUserSn);
			}
		}
		// 이미지 행 숨김 후 첨부 정리(soft delete + 물리삭제 best-effort) — ProfileImage 와 동일 순서
		fileService.softDeleteByAtchFileSn(img.getAtchFileSn(), actorUserSn);
		// 대표이미지를 비워(true→false) ACTIVE 고택이 미달로 전환되면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), ownerPrtnrSn, before);
	}

	private void setRepresentativeCore(HouseImageRepresentativeRequest request, long ownerPrtnrSn, long actorUserSn) {
		requireOwnedActiveHouse(request.houseSn(), ownerPrtnrSn);
		ReadinessSnapshot before = publishGuard.snapshotIfActive(request.houseSn(), ownerPrtnrSn);
		lockOwnedImage(request.houseImgSn(), request.houseSn());
		houseImageMapper.clearRepresentativeByHouse(request.houseSn(), actorUserSn);
		if (houseImageMapper.setRepresentative(request.houseImgSn(), request.houseSn(), actorUserSn) == 0) {
			throw new BusinessException(HouseErrorCode.HOUSE_IMAGE_NOT_FOUND);
		}
		// 발행 후 표류 방지(비퇴행) — 대표 변경/해제로 대표이미지가 사라지는 전환이면 거부(롤백)
		publishGuard.enforceNoRegression(request.houseSn(), ownerPrtnrSn, before);
	}

	// ------------------------------------------------------------------

	private long requirePrtnrSn(AuthUser partner) {
		if (partner == null) {
			throw BusinessException.unauthorized();
		}
		Long prtnrSn = partnerService.findManageablePrtnrSn(partner.userSn());
		if (prtnrSn == null) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_PARTNER);
		}
		return prtnrSn;
	}

	private void requireOwnedActiveHouse(long houseSn, long prtnrSn) {
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
	}

	/** 이미지 잠금 + 해당 고택 소속·활성 검증(부모 고택은 호출 전 선잠금). */
	private HouseImageVO lockOwnedImage(long houseImgSn, long houseSn) {
		HouseImageVO img = houseImageMapper.selectHouseImageForUpdate(houseImgSn);
		if (img == null || Y.equals(img.getDelYn()) || !img.getHouseSn().equals(houseSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_IMAGE_NOT_FOUND);
		}
		return img;
	}
}
