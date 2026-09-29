package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import egovframework.eeum.house.exception.RoomErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.dao.RoomImageMapper;
import egovframework.eeum.house.service.image.impl.RoomImageLinker;
import egovframework.eeum.house.service.image.impl.RoomImageServiceImpl;
import egovframework.eeum.house.service.room.dao.RoomMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.vo.RoomImageVO;
import egovframework.eeum.house.vo.RoomVO;
import egovframework.eeum.house.web.dto.RoomImageDeleteRequest;
import egovframework.eeum.house.web.dto.RoomImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.RoomImageResponse;

/**
 * 객실 이미지 서비스 검증 — 업로드 보상삭제/소유권(ROOM→HOUSE)/대표 자동승격(DB·SFTP 없이 mock).
 */
class RoomImageServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ROOM = 11L;
	private static final long ATCH = 50L;

	private final RoomImageMapper imageMapper = mock(RoomImageMapper.class);
	private final RoomMapper roomMapper = mock(RoomMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final FileService fileService = mock(FileService.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final RoomImageLinker linker = mock(RoomImageLinker.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final RoomImageServiceImpl service = new RoomImageServiceImpl(imageMapper, roomMapper, houseMapper,
			fileService, partnerService, linker, proxyResolver, publishGuard, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static RoomVO roomDetail(long ownerPrtnr) {
		return RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).ownerPrtnrSn(ownerPrtnr).build();
	}

	private static RoomImageVO img(long imgSn, long roomSn, String rprs) {
		return RoomImageVO.builder().roomImgSn(imgSn).roomSn(roomSn).atchFileSn(ATCH).rprsYn(rprs).delYn("N").build();
	}

	private static FileUploadResponse stored() {
		return new FileUploadResponse(ATCH, "a.jpg", "https://cdn/x.jpg", "image/jpeg", "jpg", 1000L);
	}

	/** delete/setRepresentative 의 HOUSE→ROOM 잠금 경로 스텁. */
	private void stubLock() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE))
				.thenReturn(HouseVO.builder().houseSn(HOUSE).prtnrSn(MY_PRTNR).delYn("N").build());
		when(roomMapper.selectRoomForUpdate(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).delYn("N").build());
	}

	@Test
	@DisplayName("추가 — 소유 객실이면 업로드 후 연결")
	void addSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(fileService.upload(any(), eq(7L), eq("ROOM"), eq("11"))).thenReturn(stored());
		when(linker.link(ROOM, MY_PRTNR, ATCH, 7L)).thenReturn(
				RoomImageVO.builder().roomImgSn(1L).roomSn(ROOM).atchFileSn(ATCH).sortSeq(1).rprsYn("Y").build());

		RoomImageResponse res = service.addImage(ROOM, mock(MultipartFile.class), partner(7));

		assertEquals(1L, res.roomImgSn());
		verify(linker).link(ROOM, MY_PRTNR, ATCH, 7L);
	}

	@Test
	@DisplayName("추가 — 타 소유 객실은 업로드 전 404")
	void addForeignRoom() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(999L));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.addImage(ROOM, mock(MultipartFile.class), partner(7)));
		assertEquals(RoomErrorCode.ROOM_NOT_FOUND, e.getErrorCode());
		verify(fileService, never()).upload(any(), anyLong(), anyString(), anyString());
	}

	@Test
	@DisplayName("추가 — 연결 실패 시 첨부 보상삭제")
	void addCompensatesOnLinkFailure() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(fileService.upload(any(), eq(7L), eq("ROOM"), eq("11"))).thenReturn(stored());
		when(linker.link(ROOM, MY_PRTNR, ATCH, 7L)).thenThrow(new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

		assertThrows(BusinessException.class, () -> service.addImage(ROOM, mock(MultipartFile.class), partner(7)));
		verify(fileService).softDeleteByAtchFileSn(ATCH, 7L);
	}

	@Test
	@DisplayName("삭제 — 대표 삭제 시 가장 앞 이미지 자동 승격 + 첨부 정리")
	void deleteRepresentativePromotes() {
		stubLock();
		when(imageMapper.selectRoomImageForUpdate(21L)).thenReturn(img(21L, ROOM, "Y"));
		when(imageMapper.softDeleteRoomImage(21L, ROOM, 7L)).thenReturn(1);
		when(imageMapper.selectFirstActiveImageSnByRoom(ROOM)).thenReturn(22L);

		service.delete(new RoomImageDeleteRequest(ROOM, 21L), partner(7));

		verify(imageMapper).softDeleteRoomImage(21L, ROOM, 7L);
		verify(imageMapper).setRepresentative(22L, ROOM, 7L);
		verify(fileService).softDeleteByAtchFileSn(ATCH, 7L);
	}

	@Test
	@DisplayName("삭제 — 타 객실 소속 이미지는 404")
	void deleteForeignImage() {
		stubLock();
		when(imageMapper.selectRoomImageForUpdate(21L)).thenReturn(img(21L, 999L, "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.delete(new RoomImageDeleteRequest(ROOM, 21L), partner(7)));
		assertEquals(RoomErrorCode.ROOM_IMAGE_NOT_FOUND, e.getErrorCode());
		verify(imageMapper, never()).softDeleteRoomImage(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("대표지정 — 기존 대표 해제 후 교체")
	void setRepresentativeSuccess() {
		stubLock();
		when(imageMapper.selectRoomImageForUpdate(21L)).thenReturn(img(21L, ROOM, "N"));
		when(imageMapper.setRepresentative(21L, ROOM, 7L)).thenReturn(1);

		service.setRepresentative(new RoomImageRepresentativeRequest(ROOM, 21L), partner(7));

		verify(imageMapper).clearRepresentativeByRoom(ROOM, 7L);
		verify(imageMapper).setRepresentative(21L, ROOM, 7L);
	}

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=resolver 해석, 행위자=운영자 =====

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	@Test
	@DisplayName("운영자 대행 추가 — owner는 resolver 해석(토큰 prtnrSn 미사용), 행위자=운영자 userSn")
	void addAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectRoomDetail(ROOM)).thenReturn(roomDetail(MY_PRTNR));
		when(fileService.upload(any(), eq(9L), eq("ROOM"), eq("11"))).thenReturn(stored());
		when(linker.link(ROOM, MY_PRTNR, ATCH, 9L)).thenReturn(img(1L, ROOM, "Y"));

		service.addImageAsOperator(ROOM, mock(MultipartFile.class), operator(9));

		verify(linker).link(ROOM, MY_PRTNR, ATCH, 9L); // owner=파트너, 행위자=운영자(9)
		verify(partnerService, never()).findManageablePrtnrSn(anyLong()); // 운영자는 토큰 prtnrSn 해석 안 함
	}

	@Test
	@DisplayName("운영자 대행 추가 — 정지(비승인/비활성) 파트너 객실은 resolver가 차단(업로드 진입 안 함)")
	void addAsOperatorBlockedWhenOwnerSuspended() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any()))
				.thenThrow(BusinessException.conflict("승인된 활성 파트너의 고택만 대행 관리할 수 있습니다."));

		assertThrows(BusinessException.class,
				() -> service.addImageAsOperator(ROOM, mock(MultipartFile.class), operator(9)));
		verify(fileService, never()).upload(any(), anyLong(), anyString(), anyString());
	}

	@Test
	@DisplayName("운영자 대행 삭제 — resolver owner로 코어 위임, 행위자=운영자 userSn")
	void deleteAsOperator() {
		when(proxyResolver.resolveOwnerForRoom(eq(ROOM), any())).thenReturn(MY_PRTNR);
		when(roomMapper.selectHouseSnByRoom(ROOM)).thenReturn(HOUSE);
		when(houseMapper.selectHouseForUpdate(HOUSE))
				.thenReturn(HouseVO.builder().houseSn(HOUSE).prtnrSn(MY_PRTNR).delYn("N").build());
		when(roomMapper.selectRoomForUpdate(ROOM))
				.thenReturn(RoomVO.builder().roomSn(ROOM).houseSn(HOUSE).delYn("N").build());
		when(imageMapper.selectRoomImageForUpdate(21L)).thenReturn(img(21L, ROOM, "N"));
		when(imageMapper.softDeleteRoomImage(21L, ROOM, 9L)).thenReturn(1);

		service.deleteAsOperator(new RoomImageDeleteRequest(ROOM, 21L), operator(9));

		verify(imageMapper).softDeleteRoomImage(21L, ROOM, 9L); // 행위자=운영자(9)
	}
}
