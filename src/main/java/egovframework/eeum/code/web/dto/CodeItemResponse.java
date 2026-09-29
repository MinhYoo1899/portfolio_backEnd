package egovframework.eeum.code.web.dto;

import egovframework.eeum.code.vo.CommCdVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공통코드 항목 응답 DTO. 값 비교는 {@code cd}, 화면 표시는 {@code cdNm}, 정렬은 {@code sortSeq}.
 *
 * @param groupCd 그룹코드
 * @param cd      코드(값 비교용 — 분기 기준)
 * @param cdNm    코드명(표시용 — 운영 중 변경 가능, 분기 금지)
 * @param sortSeq 정렬순서
 */
@Schema(description = "공통코드 항목")
public record CodeItemResponse(
		@Schema(description = "그룹코드", example = "USER_TYPE") String groupCd,
		@Schema(description = "코드(값 비교용)", example = "MEMBER") String cd,
		@Schema(description = "코드명(표시용)", example = "일반회원") String cdNm,
		@Schema(description = "정렬순서", example = "1") int sortSeq) {

	public static CodeItemResponse from(CommCdVO vo) {
		return new CodeItemResponse(
				vo.getGroupCd(),
				vo.getCd(),
				vo.getCdNm(),
				vo.getSortSeq() == null ? 0 : vo.getSortSeq());
	}
}
