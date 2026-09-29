package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.export.ExcelThumbnailEncoder;
import egovframework.eeum.house.service.export.impl.HouseExcelExportServiceImpl;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;

/**
 * 운영자 상품목록 엑셀 내보내기 검증 — 상한/이미지 fallback/감사기록(DB·SFTP 없이 mock).
 */
class HouseExcelExportServiceVerification {

	private static final long ATCH = 50L;

	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final FileService fileService = mock(FileService.class);
	private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
	private final ExcelThumbnailEncoder thumbnailEncoder = new ExcelThumbnailEncoder();
	private final HouseExcelExportServiceImpl service =
			new HouseExcelExportServiceImpl(houseMapper, fileService, thumbnailEncoder, auditRecorder);

	private static AuthUser operator() {
		return new AuthUser(9L, "op@eeum.dev", "운영자", "OPERATOR", "ACTIVE", List.of("HOUSE_EXPORT"));
	}

	private static OperatorHouseSearchRequest request() {
		return new OperatorHouseSearchRequest(1, 20, null, null, null, null, null);
	}

	private static HouseVO house(long sn, Long rprsAtchFileSn) {
		return HouseVO.builder()
				.houseSn(sn)
				.houseNm("고택" + sn)
				.prtnrConm("상호" + sn)
				.houseSttsCd("ACTIVE")
				.addr("경북 안동시 " + sn)
				.roomCnt(3)
				.regDt(LocalDateTime.of(2026, 6, 26, 10, 30))
				.rprsAtchFileSn(rprsAtchFileSn)
				.build();
	}

	private static byte[] smallPng() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", baos);
		return baos.toByteArray();
	}

	private static void assertXlsx(byte[] bytes) {
		assertTrue(bytes.length > 0, "byte[] 비어있지 않아야 함");
		// xlsx = ZIP → 시그니처 'PK'(0x50 0x4B)
		assertEquals(0x50, bytes[0] & 0xFF);
		assertEquals(0x4B, bytes[1] & 0xFF);
	}

	@Test
	@DisplayName("상한 이내 + 일부 대표이미지 — xlsx(ZIP) 반환 + 감사기록")
	void exportWithImages() throws Exception {
		byte[] png = smallPng();
		when(houseMapper.countHouseListForOperator(any(), any(), any(), any(), any())).thenReturn(2L);
		when(houseMapper.selectHouseListForOperator(any(), any(), any(), any(), any(), anyInt(), eq(0)))
				.thenReturn(List.of(house(1L, ATCH), house(2L, null)));
		when(fileService.download(ATCH)).thenReturn(new FileDownload("rep.png", "image/png", (long) png.length, "k/rep.png"));
		when(fileService.openStream("k/rep.png"))
				.thenReturn(new StoredResource(new ByteArrayInputStream(png), png.length, () -> {
				}));

		byte[] result = service.exportOperatorHouses(request(), operator());

		assertXlsx(result);
		verify(auditRecorder).recordSuccess(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("상한(500) 초과 — HOUSE_EXPORT_TOO_MANY, 목록 조회 미수행")
	void exportTooMany() {
		when(houseMapper.countHouseListForOperator(any(), any(), any(), any(), any())).thenReturn(501L);

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.exportOperatorHouses(request(), operator()));

		assertEquals(HouseErrorCode.HOUSE_EXPORT_TOO_MANY, e.getErrorCode());
		verify(houseMapper, never()).selectHouseListForOperator(any(), any(), any(), any(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("이미지 fetch 실패 — 전체 실패 없이 xlsx 정상 반환(빈 셀 fallback)")
	void exportImageFetchFailFallback() {
		when(houseMapper.countHouseListForOperator(any(), any(), any(), any(), any())).thenReturn(1L);
		when(houseMapper.selectHouseListForOperator(any(), any(), any(), any(), any(), anyInt(), eq(0)))
				.thenReturn(List.of(house(1L, ATCH)));
		when(fileService.download(anyLong())).thenThrow(new RuntimeException("download boom"));

		byte[] result = service.exportOperatorHouses(request(), operator());

		assertXlsx(result);
		verify(auditRecorder).recordSuccess(anyString(), anyString(), anyString());
	}
}
