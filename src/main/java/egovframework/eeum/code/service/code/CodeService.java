package egovframework.eeum.code.service.code;

import java.util.List;

import egovframework.eeum.code.web.dto.CodeItemResponse;

/**
 * 공통코드 서비스 — 상태/유형 코드 조회.
 */
public interface CodeService {

	/**
	 * 그룹의 노출 가능 공통코드 목록 (USE_YN='Y', 정렬순서 순). 미존재/빈 그룹은 빈 목록.
	 *
	 * @param groupCd 그룹코드
	 */
	List<CodeItemResponse> list(String groupCd);
}
