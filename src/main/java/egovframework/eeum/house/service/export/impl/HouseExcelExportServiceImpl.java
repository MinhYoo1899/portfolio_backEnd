package egovframework.eeum.house.service.export.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.export.ExcelThumbnailEncoder;
import egovframework.eeum.house.service.export.HouseExcelExportService;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseVO;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영자 상품(고택) 목록 엑셀 내보내기 구현 — 동기 MVP.
 *
 * <p>흐름: count → 상한(500) 검사 → 전체결과 조회(목록 매퍼 재사용, limit=상한) → 대표이미지 썸네일 동시 fetch(6, 5초 timeout, fallback)
 * → SXSSF 워크북 작성(수식 인젝션 escape + 이미지 anchor) → ByteArrayOutputStream → finally dispose → 감사기록 → byte[] 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseExcelExportServiceImpl implements HouseExcelExportService {

	/** 내보내기 행수 상한 — 초과 시 거부(검색조건 좁히기 유도). */
	static final int MAX_EXPORT_ROWS = 500;

	/** 이미지 fetch 동시성 상한. */
	private static final int IMAGE_FETCH_THREADS = 6;

	/** per-image fetch timeout(초). */
	private static final int IMAGE_FETCH_TIMEOUT_SEC = 5;

	/** 대표이미지 컬럼 인덱스(헤더 순서 마지막). */
	private static final int IMG_COL = 6;

	private static final String[] HEADERS =
			{"고택명", "상호(판매자)", "상태", "주소", "객실수", "등록일시", "대표이미지"};

	private static final DateTimeFormatter REG_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final HouseMapper houseMapper;
	private final FileService fileService;
	private final ExcelThumbnailEncoder thumbnailEncoder;
	private final AuditRecorder auditRecorder;

	@Override
	public byte[] exportOperatorHouses(OperatorHouseSearchRequest request, AuthUser operator) {
		String status = trimToNull(request.status());
		String keyword = escapeLike(trimToNull(request.keyword()));
		Long prtnrSn = request.prtnrSn();
		String sidoRegionCd = trimToNull(request.sidoRegionCd());
		String sigunguRegionCd = trimToNull(request.sigunguRegionCd());

		long total = houseMapper.countHouseListForOperator(status, keyword, prtnrSn, sidoRegionCd, sigunguRegionCd);
		if (total > MAX_EXPORT_ROWS) {
			throw new BusinessException(HouseErrorCode.HOUSE_EXPORT_TOO_MANY);
		}

		// 전체결과(상한까지) — 목록 매퍼 재사용. limit=상한, offset=0.
		List<HouseVO> houses = houseMapper.selectHouseListForOperator(status, keyword, prtnrSn,
				sidoRegionCd, sigunguRegionCd, MAX_EXPORT_ROWS, 0);

		// 대표이미지 썸네일 동시 fetch(있는 행만). 실패/timeout 은 null fallback.
		List<byte[]> thumbnails = fetchThumbnails(houses);

		byte[] bytes = buildWorkbook(houses, thumbnails);

		boolean withImage = thumbnails.stream().anyMatch(b -> b != null);
		auditRecorder.recordSuccess(
				String.format(
						"HOUSE_EXPORT {\"status\":\"%s\",\"keyword\":\"%s\",\"prtnrSn\":%s,\"count\":%d,\"withImage\":%b}",
						jsonSafe(status), jsonSafe(request.keyword()), prtnrSn, houses.size(), withImage),
				"HOUSE", "EXPORT");
		return bytes;
	}

	// ------------------------------------------------------------------

	/** 대표이미지(rprsAtchFileSn) 있는 행만 동시 fetch — houses 와 같은 순서의 썸네일 PNG(없으면 null) 리스트. */
	private List<byte[]> fetchThumbnails(List<HouseVO> houses) {
		List<byte[]> result = new ArrayList<>(houses.size());
		List<Future<byte[]>> futures = new ArrayList<>(houses.size());
		ExecutorService pool = Executors.newFixedThreadPool(IMAGE_FETCH_THREADS);
		try {
			for (HouseVO house : houses) {
				Long atchFileSn = house.getRprsAtchFileSn();
				if (atchFileSn == null) {
					futures.add(null);
					continue;
				}
				futures.add(pool.submit(thumbnailTask(atchFileSn)));
			}
			for (Future<byte[]> future : futures) {
				result.add(future == null ? null : resolveThumbnail(future));
			}
		} finally {
			pool.shutdownNow();
		}
		return result;
	}

	private Callable<byte[]> thumbnailTask(long atchFileSn) {
		return () -> {
			byte[] source = loadImageBytes(atchFileSn);
			return source == null ? null : thumbnailEncoder.toThumbnailPng(source);
		};
	}

	private byte[] resolveThumbnail(Future<byte[]> future) {
		try {
			return future.get(IMAGE_FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			// timeout/실행예외 — 전체 내보내기를 막지 않고 해당 셀만 빈칸 fallback
			log.debug("[HOUSE_EXPORT] 대표이미지 fetch 실패/timeout — 빈 셀로 대체", e);
			return null;
		}
	}

	/** 첨부 1건의 원본 이미지 바이트 — download 메타 → openStream(try-with-resources). 실패면 null(fallback). */
	private byte[] loadImageBytes(long atchFileSn) {
		try {
			FileDownload fd = fileService.download(atchFileSn);
			if (fd == null || fd.strgKey() == null) {
				return null;
			}
			try (StoredResource resource = fileService.openStream(fd.strgKey())) {
				return resource.inputStream().readAllBytes();
			}
		} catch (Exception e) {
			log.debug("[HOUSE_EXPORT] 첨부 스트림 열기 실패 atchFileSn={} — 빈 셀로 대체", atchFileSn, e);
			return null;
		}
	}

	/** SXSSF 워크북 작성 — 단일스레드. dispose 는 finally 에서 호출. */
	private byte[] buildWorkbook(List<HouseVO> houses, List<byte[]> thumbnails) {
		SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSFWorkbook.DEFAULT_WINDOW_SIZE);
		try {
			SXSSFSheet sheet = workbook.createSheet("고택목록");
			writeHeader(sheet);
			sheet.setColumnWidth(IMG_COL, 256 * 30); // 썸네일 보이게 이미지 컬럼 너비 확대

			Drawing<?> drawing = sheet.createDrawingPatriarch();
			for (int i = 0; i < houses.size(); i++) {
				int rowIdx = i + 1; // 0 = 헤더
				writeDataRow(sheet, rowIdx, houses.get(i));
				byte[] thumb = thumbnails.get(i);
				if (thumb != null) {
					addThumbnail(workbook, drawing, rowIdx, thumb);
				}
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new BusinessException(HouseErrorCode.HOUSE_EXPORT_FAILED,
					HouseErrorCode.HOUSE_EXPORT_FAILED.defaultMessage(), e);
		} finally {
			workbook.dispose(); // SXSSF 임시파일 정리(반드시 finally)
		}
	}

	private void writeHeader(SXSSFSheet sheet) {
		SXSSFRow header = sheet.createRow(0);
		for (int c = 0; c < HEADERS.length; c++) {
			header.createCell(c).setCellValue(HEADERS[c]);
		}
	}

	private void writeDataRow(SXSSFSheet sheet, int rowIdx, HouseVO house) {
		SXSSFRow row = sheet.createRow(rowIdx);
		row.setHeightInPoints(130); // 썸네일(긴변 160px ≈ 120pt) 보이게 행 높이 확대
		row.createCell(0).setCellValue(safeText(house.getHouseNm()));
		row.createCell(1).setCellValue(safeText(house.getPrtnrConm()));
		row.createCell(2).setCellValue(safeText(house.getHouseSttsCd()));
		row.createCell(3).setCellValue(safeText(house.getAddr()));
		if (house.getRoomCnt() != null) {
			row.createCell(4).setCellValue(house.getRoomCnt());
		} else {
			row.createCell(4).setCellValue("");
		}
		row.createCell(5).setCellValue(house.getRegDt() == null ? "" : house.getRegDt().format(REG_DT_FMT));
		row.createCell(IMG_COL).setCellValue(""); // 이미지 셀(빈칸 기본 — 이미지는 anchor 로 덮어 그림)
	}

	private void addThumbnail(SXSSFWorkbook workbook, Drawing<?> drawing, int rowIdx, byte[] thumb) {
		int pictureIdx = workbook.addPicture(thumb, Workbook.PICTURE_TYPE_PNG);
		ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
		anchor.setCol1(IMG_COL);
		anchor.setRow1(rowIdx);
		anchor.setCol2(IMG_COL + 1);
		anchor.setRow2(rowIdx + 1);
		anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE); // 셀 크기에 맞춰 이미지 배치
		drawing.createPicture(anchor, pictureIdx);
	}

	// ------------------------------------------------------------------

	/** 수식 인젝션 방지 — '= + - @' 또는 탭/CR 로 시작하면 작은따옴표(')로 무력화. */
	private static String safeText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		char first = value.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
			return "'" + value;
		}
		return value;
	}

	/** 감사 메시지 JSON 안전화 — 따옴표/역슬래시/개행 제거(로그 인젝션 방지). */
	private static String jsonSafe(String s) {
		if (s == null) {
			return "";
		}
		return s.replaceAll("[\"\\\\\\r\\n]", " ");
	}

	private static String escapeLike(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
