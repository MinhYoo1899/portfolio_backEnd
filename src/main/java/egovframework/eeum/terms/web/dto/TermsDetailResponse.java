package egovframework.eeum.terms.web.dto;

import egovframework.eeum.terms.vo.TrmsVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 약관 상세 응답 DTO (본문 포함).
 *
 * @param trmsSn     약관일련번호
 * @param trmsTypeCd 약관유형코드
 * @param verNo      버전번호
 * @param trmsTtl    약관제목
 * @param trmsCn     약관본문
 */
@Schema(description = "약관 상세")
public record TermsDetailResponse(
		@Schema(description = "약관일련번호", example = "1") Long trmsSn,
		@Schema(description = "약관유형코드", example = "SERVICE") String trmsTypeCd,
		@Schema(description = "버전번호", example = "1.0") String verNo,
		@Schema(description = "약관제목", example = "서비스 이용약관") String trmsTtl,
		@Schema(description = "약관본문") String trmsCn) {

	public static TermsDetailResponse from(TrmsVO vo) {
		return new TermsDetailResponse(
				vo.getTrmsSn(),
				vo.getTrmsTypeCd(),
				vo.getVerNo(),
				vo.getTrmsTtl(),
				vo.getTrmsCn());
	}
}
