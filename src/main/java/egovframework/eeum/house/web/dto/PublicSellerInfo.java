package egovframework.eeum.house.web.dto;

import egovframework.eeum.house.vo.PublicHouseDetailVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 손님 공개 판매자 표시정보 — 상호/대표자/사업자번호/통신판매번호 4필드만(사용자 결정, 전부 nullable).
 *
 * <p>담당자 연락처(암호화 PII)·주소·내부 식별자(prtnrSn/userSn)는 절대 포함하지 않는다.
 */
@Schema(description = "판매자 표시정보(공개)")
public record PublicSellerInfo(

		@Schema(description = "상호(판매자명)") String conm,
		@Schema(description = "대표자명") String rprsvNm,
		@Schema(description = "사업자등록번호") String brno,
		@Schema(description = "통신판매업신고번호(미신고 시 null)") String mailOrderSalesNo) {

	public static PublicSellerInfo from(PublicHouseDetailVO vo) {
		return new PublicSellerInfo(vo.getConm(), vo.getRprsvNm(), vo.getBrno(), vo.getMailOrderSalesNo());
	}
}
