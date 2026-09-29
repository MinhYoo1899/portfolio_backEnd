package egovframework.eeum.house.service.export;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;

/**
 * 운영자 상품(고택) 목록 엑셀 내보내기 — 동기 MVP.
 *
 * <p>목록 검색조건(status/keyword/prtnrSn)을 재사용하되 페이징을 제거하고 전체결과(상한 {@code MAX_EXPORT_ROWS}까지)를
 * 내보낸다. 상한 초과면 내보내지 않고 {@code HOUSE_EXPORT_TOO_MANY} 로 거부한다. 대량 반출은 감사기록 대상.
 */
public interface HouseExcelExportService {

	/**
	 * 검색조건에 맞는 고택 목록을 xlsx 바이트로 내보낸다.
	 *
	 * @param request  목록 검색조건(page/pageSize 는 무시 — 전체결과)
	 * @param operator 내보내기 행위자(감사기록용)
	 * @return xlsx(ZIP) 바이트
	 */
	byte[] exportOperatorHouses(OperatorHouseSearchRequest request, AuthUser operator);
}
