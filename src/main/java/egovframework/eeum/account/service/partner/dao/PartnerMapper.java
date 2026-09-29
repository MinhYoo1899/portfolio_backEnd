package egovframework.eeum.account.service.partner.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.account.web.dto.PartnerSearchRequest;
import egovframework.eeum.account.vo.PartnerVO;

/**
 * 파트너 매퍼 — XML: {@code egovframework/mapper/eeum/account/PartnerMapper_mysql.xml}
 */
@Mapper
public interface PartnerMapper {

	/** 활성(DEL_YN='N') 사업자등록번호 존재 여부 (1/0). */
	int existsActiveBrno(@Param("brno") String brno);

	/**
	 * 상품 관리 가능한 파트너 일련번호 (USER_SN 기준, 미존재 시 null) — 상품(고택) 소유권 해석용.
	 * 승인완료(APRV_STTS='APPROVED') + 계정 활성(ACNT_STTS='ACTIVE')인 파트너만 해석한다.
	 */
	Long selectManageablePrtnrSnByUserSn(@Param("userSn") long userSn);

	/** 파트너 생성 — prtnrSn generated key 수신. */
	int insertPartner(PartnerVO partner);

	/** 파트너 상세 (계정 조인). */
	PartnerVO selectPartnerByPrtnrSn(@Param("prtnrSn") long prtnrSn);

	/** 파트너 목록 (검색+페이징). */
	List<PartnerVO> selectPartnerList(PartnerSearchRequest search);

	/** 파트너 목록 전체 건수. */
	long countPartnerList(PartnerSearchRequest search);

	/** 승인상태별 파트너 수 — 인증요청(REQUESTED) 헤더칩/사이드바 뱃지용. */
	long countByAprvStts(@Param("aprvStts") String aprvStts);

	/**
	 * 승인/반려 처리 — REQUESTED 상태일 때만 갱신(동시 처리 경합 방지).
	 * 반환 0 = 이미 처리됨(또는 부재).
	 */
	int updateAprvSttsIfRequested(@Param("prtnrSn") long prtnrSn,
			@Param("aprvStts") String aprvStts,
			@Param("aprvUserSn") long aprvUserSn,
			@Param("rjctRsn") String rjctRsn);

	/** 승인/반려 로그 기록 (PARTNER_APPROVE_LOG_INFO, append-only). */
	int insertApproveLog(@Param("prtnrSn") long prtnrSn,
			@Param("bfrAprvStts") String bfrAprvStts,
			@Param("aftAprvStts") String aftAprvStts,
			@Param("rsn") String rsn,
			@Param("actrUserSn") Long actrUserSn);

	/**
	 * 사업자정보 부분수정 — null 필드는 변경하지 않음. {@code resetBrnoVrfc=true}(brno 변경) 시 BRNO_VRFC_STTS='REQUESTED'.
	 * 상태(APRV_STTS/계정)는 건드리지 않는다. DEL_YN='N' 만. 영향행 0 = 부재/삭제.
	 */
	int updatePartnerInfo(@Param("prtnrSn") long prtnrSn, @Param("conm") String conm, @Param("brno") String brno,
			@Param("rprsvNm") String rprsvNm, @Param("addr") String addr, @Param("daddr") String daddr,
			@Param("mailOrderSalesNo") String mailOrderSalesNo, @Param("picMblTelno") String picMblTelno,
			@Param("picNm") String picNm, @Param("prtnrEmlAddr") String prtnrEmlAddr,
			@Param("resetBrnoVrfc") boolean resetBrnoVrfc, @Param("mdfrSn") long mdfrSn);
}
