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
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.ReadinessSnapshot;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.RoomImageService;
import egovframework.eeum.house.service.image.dao.RoomImageMapper;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomImageVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomImageDeleteRequest;
import egovframework.eeum.house.web.dto.RoomImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.RoomImageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 객실 이미지 서비스 구현 — HouseImage 와 동일 패턴(업로드 트랜잭션 밖 → 링커 → 보상삭제), 소유권은 ROOM→HOUSE→PRTNR_SN.
 *
 * <p>판매자(파트너 본인) 메서드는 토큰 owner 로, 운영자 대행 메서드는 {@link HouseProxyOwnerResolver} 로 owner 를
 * 서버 해석한 뒤 공통 코어를 공유한다(코어는 ownerPrtnrSn+actorUserSn 만 받음 — 동작/응답 동일).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomImageServiceImpl implements RoomImageService {

	private static final String Y = "Y";
	private static final String TRGT_TYPE_ROOM = "ROOM";

	private final RoomImageMapper roomImageMapper;
	private final RoomMapper roomMapper;
	private final HouseMapper houseMapper;
	private final FileService fileService;
	private final PartnerService partnerService;
	private final RoomImageLinker roomImageLinker;
	private final HouseProxyOwnerResolver proxyResolver;
	private final HousePublishGuard publishGuard;
	private final HouseReviewGuard reviewGuard;

	// ===================== 판매자(파트너 본인) — owner=토큰 해석 =====================

	@Override
	@Audit(actn = "ROOM_IMAGE_ADD", trgtType = "ROOM", trgtId = "#roomSn")
	public RoomImageResponse addImage(long roomSn, MultipartFile file, AuthUser partner) {
		return addImageCore(roomSn, requirePrtnrSn(partner), partner.userSn(), file);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomImageResponse> listByRoom(long roomSn, AuthUser partner) {
		return listCore(roomSn, requirePrtnrSn(partner));
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_IMAGE_DELETE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void delete(RoomImageDeleteRequest request, AuthUser partner) {
		deleteCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_IMAGE_REPRESENTATIVE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setRepresentative(RoomImageRepresentativeRequest request, AuthUser partner) {
		setRepresentativeCore(request, requirePrtnrSn(partner), partner.userSn());
	}

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=대상객실 서버해석, 행위자=운영자 ============

	@Override
	@Audit(actn = "ROOM_PROXY_IMAGE_ADD", trgtType = "ROOM", trgtId = "#roomSn")
	public RoomImageResponse addImageAsOperator(long roomSn, MultipartFile file, AuthUser operator) {
		RoomImageResponse result = addImageCore(roomSn, proxyResolver.resolveOwnerForRoom(roomSn, operator),
				operator.userSn(), file);
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomImageResponse> listByRoomAsOperator(long roomSn, AuthUser operator) {
		return listCore(roomSn, proxyResolver.resolveOwnerForRoom(roomSn, operator));
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_IMAGE_DELETE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void deleteAsOperator(RoomImageDeleteRequest request, AuthUser operator) {
		deleteCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ROOM_PROXY_IMAGE_REPRESENTATIVE", trgtType = "ROOM", trgtId = "#request.roomSn()")
	public void setRepresentativeAsOperator(RoomImageRepresentativeRequest request, AuthUser operator) {
		setRepresentativeCore(request, proxyResolver.resolveOwnerForRoom(request.roomSn(), operator), operator.userSn());
		// 검수요청 상태였으면 내용 변경으로 DRAFT 자동복귀(+REVERT 이력)
		Long houseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		if (houseSn != null) {
			reviewGuard.markDirtyIfUnderReview(houseSn, operator.userSn());
		}
	}

	// ============ 코어(소유자=ownerPrtnrSn, 행위자=actorUserSn 공유 — 파트너/운영자 동일 로직) ============

	private RoomImageResponse addImageCore(long roomSn, long ownerPrtnrSn, long actorUserSn, MultipartFile file) {
		RoomVO pre = roomMapper.selectRoomDetail(roomSn);
		if (pre == null || pre.getOwnerPrtnrSn() == null || pre.getOwnerPrtnrSn() != ownerPrtnrSn) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		FileUploadResponse stored = fileService.upload(file, actorUserSn, TRGT_TYPE_ROOM, String.valueOf(roomSn));
		RoomImageVO created;
		try {
			// 보상삭제 catch 는 "행삽입 실패"에만 걸리도록 좁힌다(codex MUST) — 응답 생성은 try 밖
			created = roomImageLinker.link(roomSn, ownerPrtnrSn, stored.atchFileSn(), actorUserSn);
		} catch (RuntimeException e) {
			compensate(stored.atchFileSn(), actorUserSn);
			throw e;
		}
		created.setUrlAddr(stored.downloadUrl());
		created.setOrgnlFileNm(stored.orgnlFileNm());
		return RoomImageResponse.from(created);
	}

	/** 행삽입 실패 보상 — 보상 실패가 원 예외를 덮지 않도록 격리(실패분은 정리배치가 회수). */
	private void compensate(long atchFileSn, long actorUserSn) {
		try {
			fileService.softDeleteByAtchFileSn(atchFileSn, actorUserSn);
		} catch (RuntimeException ex) {
			log.warn("[ROOM_IMAGE] 첨부 보상삭제 실패(정리배치 회수 대상) atchFileSn={}", atchFileSn, ex);
		}
	}

	private List<RoomImageResponse> listCore(long roomSn, long ownerPrtnrSn) {
		RoomVO room = roomMapper.selectRoomDetail(roomSn);
		if (room == null || room.getOwnerPrtnrSn() == null || room.getOwnerPrtnrSn() != ownerPrtnrSn) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		return roomImageMapper.selectActiveImagesByRoom(roomSn).stream()
				.map(RoomImageResponse::from).toList();
	}

	private void deleteCore(RoomImageDeleteRequest request, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		// 발행 후 표류 방지(비퇴행) — 변경 전 부모 고택 readiness 캡처(부모 미해석/비ACTIVE면 null)
		Long guardHouseSn = roomMapper.selectHouseSnByRoom(request.roomSn());
		ReadinessSnapshot before = guardHouseSn == null ? null : publishGuard.snapshotIfActive(guardHouseSn, ownerPrtnrSn);
		RoomImageVO img = lockOwnedImage(request.roomImgSn(), request.roomSn());
		boolean wasRepresentative = Y.equals(img.getRprsYn());
		if (roomImageMapper.softDeleteRoomImage(request.roomImgSn(), request.roomSn(), actorUserSn) == 0) {
			throw new BusinessException(RoomErrorCode.ROOM_IMAGE_NOT_FOUND);
		}
		if (wasRepresentative) {
			Long first = roomImageMapper.selectFirstActiveImageSnByRoom(request.roomSn());
			if (first != null) {
				roomImageMapper.setRepresentative(first, request.roomSn(), actorUserSn);
			}
		}
		fileService.softDeleteByAtchFileSn(img.getAtchFileSn(), actorUserSn);
		// 객실이미지를 비워(게시가능 객실 소멸) ACTIVE 고택이 미달로 전환되면 거부(롤백)
		if (guardHouseSn != null) {
			publishGuard.enforceNoRegression(guardHouseSn, ownerPrtnrSn, before);
		}
	}

	private void setRepresentativeCore(RoomImageRepresentativeRequest request, long ownerPrtnrSn, long actorUserSn) {
		lockOwnedActiveRoom(request.roomSn(), ownerPrtnrSn);
		lockOwnedImage(request.roomImgSn(), request.roomSn());
		roomImageMapper.clearRepresentativeByRoom(request.roomSn(), actorUserSn);
		if (roomImageMapper.setRepresentative(request.roomImgSn(), request.roomSn(), actorUserSn) == 0) {
			throw new BusinessException(RoomErrorCode.ROOM_IMAGE_NOT_FOUND);
		}
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

	/** 락 순서 HOUSE→ROOM 통일: 부모 고택 선잠금(소유·활성) → 객실 잠금(활성). */
	private void lockOwnedActiveRoom(long roomSn, long prtnrSn) {
		Long houseSn = roomMapper.selectHouseSnByRoom(roomSn);
		if (houseSn == null) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
		RoomVO room = roomMapper.selectRoomForUpdate(roomSn);
		if (room == null || Y.equals(room.getDelYn())) {
			throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
		}
	}

	/** 이미지 잠금 + 해당 객실 소속·활성 검증(부모 객실은 호출 전 선잠금). */
	private RoomImageVO lockOwnedImage(long roomImgSn, long roomSn) {
		RoomImageVO img = roomImageMapper.selectRoomImageForUpdate(roomImgSn);
		if (img == null || Y.equals(img.getDelYn()) || !img.getRoomSn().equals(roomSn)) {
			throw new BusinessException(RoomErrorCode.ROOM_IMAGE_NOT_FOUND);
		}
		return img;
	}
}
