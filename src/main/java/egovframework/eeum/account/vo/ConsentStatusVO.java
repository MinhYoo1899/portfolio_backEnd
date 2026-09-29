package egovframework.eeum.account.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 내 동의 현황 VO — 약관 유형별 "현재 동의상태"(TRMS_AGRE_INFO 의 유형별 최신 행) 운반용.
 *
 * <p>최신 판정 = AGRE_DT(동의/철회 발생시각) DESC, 동률 시 TRMS_AGRE_SN DESC (TRMS_AGRE_INFO 설계 주석과 동일).
 * 응답(Response)으로 직접 노출하지 않고 ConsentStatusResponse 로 변환(발생시각은 Instant=UTC).
 */
@Getter
@Setter
public class ConsentStatusVO {

	/** 약관일련번호(유형+버전 특정) */
	private Long trmsSn;

	/** 약관유형코드(공통코드 TRMS_TYPE) */
	private String trmsTypeCd;

	/** 버전번호 */
	private String verNo;

	/** 약관제목 */
	private String trmsTtl;

	/** 필수여부(Y/N) */
	private String esntlYn;

	/** 현재 동의여부(Y=동의 / N=철회) */
	private String agreYn;

	/** 현재 상태의 동의/철회 발생일시(UTC 저장) */
	private LocalDateTime agreDt;
}
