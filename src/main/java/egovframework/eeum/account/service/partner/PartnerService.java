package egovframework.eeum.account.service.partner;

import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.account.web.dto.PartnerInsertRequest;
import egovframework.eeum.account.web.dto.PartnerListItemResponse;
import egovframework.eeum.account.web.dto.PartnerSearchRequest;
import egovframework.eeum.account.web.dto.PartnerSelfResponse;
import egovframework.eeum.account.web.dto.PartnerUpdateRequest;
import egovframework.eeum.base.response.PageResponse;

/**
 * 파트너 서비스 — 사업자 가입 신청 + 운영자 승인 워크플로우 + 사업자정보 수정.
 */
public interface PartnerService {

	/** 파트너 가입 신청 — 계정(PENDING)+사업자(REQUESTED) 생성, 승인이력 기록. */
	PartnerDetailResponse insert(PartnerInsertRequest request, String cntnIp, String userAgnt);

	/**
	 * 로그인 사용자의 상품 관리 가능한 파트너 일련번호 해석 — 상품(고택) 소유권 기준.
	 * 승인완료 + 계정활성 파트너만 해석하며, 아니면 {@code null}(승인 전·정지·반려 파트너 차단).
	 */
	Long findManageablePrtnrSn(long userSn);

	/** 파트너 목록 (운영자 — 서버사이드 페이징). */
	PageResponse<PartnerListItemResponse> list(PartnerSearchRequest request);

	/** 인증요청(가입 승인 대기, aprvStts=REQUESTED) 수 — 헤더칩/사이드바 뱃지용. */
	long pendingCount();

	/** 파트너 상세 (운영자). */
	PartnerDetailResponse detail(long prtnrSn);

	/**
	 * 파트너 본인(PTNS) 사업자정보 조회 — 토큰 userSn 으로 본인 prtnrSn 해석(파라미터 미수신, IDOR 차단).
	 * APPROVED+ACTIVE 본인만 반환하며, 아니면(비승인/정지/해석실패/삭제) 403({@code PARTNER_NOT_ACCESSIBLE}).
	 * 운영자용 {@link PartnerDetailResponse} 가 아니라 {@link PartnerSelfResponse}(마스킹·내부필드 제외)를 반환한다.
	 */
	PartnerSelfResponse me(long userSn);

	/** 승인 — REQUESTED→APPROVED + 계정 ACTIVE + 이력. 이미 처리된 건은 409. */
	void approve(long prtnrSn, long actorUserSn);

	/** 반려 — REQUESTED→REJECTED(사유 필수) + 계정 REJECTED + 이력. */
	void reject(long prtnrSn, String rjctRsn, long actorUserSn);

	/**
	 * 사업자정보 부분수정(운영자 PRTNR_APRV) — 미포함/null 필드는 보존, 상태(승인/계정)는 불변.
	 * brno 가 실제 변경되면 사업자번호검증상태를 REQUESTED 로 초기화한다. DEL_YN='N' 파트너만. 수정 후 상세 반환.
	 */
	PartnerDetailResponse updateInfo(PartnerUpdateRequest request, long actorUserSn);
}
