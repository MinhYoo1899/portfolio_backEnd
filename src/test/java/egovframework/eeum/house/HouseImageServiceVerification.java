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
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.HouseProxyOwnerResolver;
import egovframework.eeum.house.service.house.HousePublishGuard;
import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.service.image.impl.HouseImageLinker;
import egovframework.eeum.house.service.image.impl.HouseImageServiceImpl;
import egovframework.eeum.house.vo.HouseImageVO;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.HouseImageDeleteRequest;
import egovframework.eeum.house.web.dto.HouseImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.HouseImageResponse;

/**
 * 고택 이미지 서비스 검증 — 업로드 보상삭제/소유권/대표 자동승격(DB·SFTP 없이 mock).
 */
class HouseImageServiceVerification {

	private static final long MY_PRTNR = 100L;
	private static final long HOUSE = 5L;
	private static final long ATCH = 50L;

	private final HouseImageMapper imageMapper = mock(HouseImageMapper.class);
	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final FileService fileService = mock(FileService.class);
	private final PartnerService partnerService = mock(PartnerService.class);
	private final HouseImageLinker linker = mock(HouseImageLinker.class);
	private final HouseProxyOwnerResolver proxyResolver = mock(HouseProxyOwnerResolver.class);
	private final HousePublishGuard publishGuard = mock(HousePublishGuard.class);
	private final HouseReviewGuard reviewGuard = mock(HouseReviewGuard.class);
	private final HouseImageServiceImpl service = new HouseImageServiceImpl(
			imageMapper, houseMapper, fileService, partnerService, linker, proxyResolver, publishGuard, reviewGuard);

	private static AuthUser partner(long userSn) {
		return new AuthUser(userSn, "p@eeum.kr", "김파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
	}

	private static HouseVO house(long prtnrSn, String delYn) {
		return HouseVO.builder().houseSn(HOUSE).prtnrSn(prtnrSn).delYn(delYn).build();
	}

	private static HouseImageVO img(long imgSn, long houseSn, String rprs) {
		return HouseImageVO.builder().houseImgSn(imgSn).houseSn(houseSn).atchFileSn(ATCH)
				.rprsYn(rprs).delYn("N").build();
	}

	private static FileUploadResponse stored() {
		return new FileUploadResponse(ATCH, "a.jpg", "https://cdn/x.jpg", "image/jpeg", "jpg", 1000L);
	}

	@Test
	@DisplayName("추가 — 소유 고택이면 업로드 후 연결, 첫 이미지 응답")
	void addSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(fileService.upload(any(), eq(7L), eq("HOUSE"), eq("5"))).thenReturn(stored());
		when(linker.link(HOUSE, MY_PRTNR, ATCH, 7L)).thenReturn(
				HouseImageVO.builder().houseImgSn(1L).houseSn(HOUSE).atchFileSn(ATCH).sortSeq(1).rprsYn("Y").build());

		HouseImageResponse res = service.addImage(HOUSE, mock(MultipartFile.class), partner(7));

		assertEquals(1L, res.houseImgSn());
		assertEquals("https://cdn/x.jpg", res.urlAddr());
		verify(linker).link(HOUSE, MY_PRTNR, ATCH, 7L);
	}

	@Test
	@DisplayName("추가 — 타 소유 고택은 업로드 전 404")
	void addForeignHouse() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(house(999L, "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.addImage(HOUSE, mock(MultipartFile.class), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_NOT_FOUND, e.getErrorCode());
		verify(fileService, never()).upload(any(), anyLong(), anyString(), anyString());
	}

	@Test
	@DisplayName("추가 — 연결 실패 시 업로드한 첨부 보상삭제 후 예외 전파")
	void addCompensatesOnLinkFailure() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(fileService.upload(any(), eq(7L), eq("HOUSE"), eq("5"))).thenReturn(stored());
		when(linker.link(HOUSE, MY_PRTNR, ATCH, 7L)).thenThrow(new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND));

		assertThrows(BusinessException.class, () -> service.addImage(HOUSE, mock(MultipartFile.class), partner(7)));
		verify(fileService).softDeleteByAtchFileSn(ATCH, 7L);
	}

	@Test
	@DisplayName("삭제 — 대표 삭제 시 가장 앞 이미지 자동 승격 + 첨부 정리")
	void deleteRepresentativePromotes() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(imageMapper.selectHouseImageForUpdate(11L)).thenReturn(img(11L, HOUSE, "Y"));
		when(imageMapper.softDeleteHouseImage(11L, HOUSE, 7L)).thenReturn(1);
		when(imageMapper.selectFirstActiveImageSnByHouse(HOUSE)).thenReturn(12L);

		service.delete(new HouseImageDeleteRequest(HOUSE, 11L), partner(7));

		verify(imageMapper).softDeleteHouseImage(11L, HOUSE, 7L);
		verify(imageMapper).setRepresentative(12L, HOUSE, 7L);
		verify(fileService).softDeleteByAtchFileSn(ATCH, 7L);
	}

	@Test
	@DisplayName("삭제 — 타 고택 소속 이미지는 404")
	void deleteForeignImage() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(imageMapper.selectHouseImageForUpdate(11L)).thenReturn(img(11L, 999L, "N"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.delete(new HouseImageDeleteRequest(HOUSE, 11L), partner(7)));
		assertEquals(HouseErrorCode.HOUSE_IMAGE_NOT_FOUND, e.getErrorCode());
		verify(imageMapper, never()).softDeleteHouseImage(anyLong(), anyLong(), anyLong());
	}

	@Test
	@DisplayName("대표지정 — 기존 대표 해제 후 교체")
	void setRepresentativeSuccess() {
		when(partnerService.findManageablePrtnrSn(7L)).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(imageMapper.selectHouseImageForUpdate(11L)).thenReturn(img(11L, HOUSE, "N"));
		when(imageMapper.setRepresentative(11L, HOUSE, 7L)).thenReturn(1);

		service.setRepresentative(new HouseImageRepresentativeRequest(HOUSE, 11L), partner(7));

		verify(imageMapper).clearRepresentativeByHouse(HOUSE, 7L);
		verify(imageMapper).setRepresentative(11L, HOUSE, 7L);
	}

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=resolver 해석, 행위자=운영자 =====

	private static AuthUser operator(long userSn) {
		return new AuthUser(userSn, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_PROXY_MNG"));
	}

	@Test
	@DisplayName("운영자 대행 추가 — owner는 resolver 해석(토큰 prtnrSn 미사용), 행위자=운영자 userSn")
	void addAsOperator() {
		when(proxyResolver.resolveOwnerForHouse(eq(HOUSE), any())).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseDetail(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(fileService.upload(any(), eq(9L), eq("HOUSE"), eq("5"))).thenReturn(stored());
		when(linker.link(HOUSE, MY_PRTNR, ATCH, 9L)).thenReturn(img(1L, HOUSE, "Y"));

		service.addImageAsOperator(HOUSE, mock(MultipartFile.class), operator(9));

		verify(linker).link(HOUSE, MY_PRTNR, ATCH, 9L); // owner=파트너, 행위자=운영자(9)
		verify(partnerService, never()).findManageablePrtnrSn(anyLong()); // 운영자는 토큰 prtnrSn 해석 안 함
	}

	@Test
	@DisplayName("운영자 대행 삭제 — resolver owner로 코어 위임, 행위자=운영자 userSn")
	void deleteAsOperator() {
		when(proxyResolver.resolveOwnerForHouse(eq(HOUSE), any())).thenReturn(MY_PRTNR);
		when(houseMapper.selectHouseForUpdate(HOUSE)).thenReturn(house(MY_PRTNR, "N"));
		when(imageMapper.selectHouseImageForUpdate(11L)).thenReturn(img(11L, HOUSE, "N"));
		when(imageMapper.softDeleteHouseImage(11L, HOUSE, 9L)).thenReturn(1);

		service.deleteAsOperator(new HouseImageDeleteRequest(HOUSE, 11L), operator(9));

		verify(imageMapper).softDeleteHouseImage(11L, HOUSE, 9L); // 행위자=운영자(9)
	}
}
