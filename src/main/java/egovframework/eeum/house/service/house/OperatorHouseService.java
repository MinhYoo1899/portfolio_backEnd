package egovframework.eeum.house.service.house;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.web.dto.HouseDailyRateSetRequest;
import egovframework.eeum.house.web.dto.HouseDeleteRequest;
import egovframework.eeum.house.web.dto.HouseDetailResponse;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseRegularClosureSetRequest;
import egovframework.eeum.house.web.dto.HouseReleaseRequest;
import egovframework.eeum.house.web.dto.HouseReviewRejectRequest;
import egovframework.eeum.house.web.dto.HouseReviewRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseSuspendRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;
import egovframework.eeum.house.web.dto.OperatorHouseDetailDrawerResponse;
import egovframework.eeum.house.web.dto.OperatorHouseFilterOptionsResponse;
import egovframework.eeum.house.web.dto.OperatorHouseInsertRequest;
import egovframework.eeum.house.web.dto.OperatorHouseListItemResponse;
import egovframework.eeum.house.web.dto.OperatorHouseSearchRequest;
import egovframework.eeum.house.web.dto.PublishCheckResponse;

/**
 * 운영자 상품(고택) 서비스 — 조회(HOUSE_VIEW) + 대행 관리(HOUSE_PROXY_MNG) + 임시조치(HOUSE_MODER).
 *
 * <p>운영자는 {@code HOUSE_PROXY_MNG} 권한으로 파트너 대신 고택을 대행 등록·수정·삭제·상태변경하고 정책·결제수단을 설정한다
 * (소유 파트너는 대상 고택에서 서버 해석, 행위자=운영자). 조회는 {@code HOUSE_VIEW}(소유권 무관 전 판매자).
 * 임시조치({@code HOUSE_MODER})는 강제 게시중지(SUSPENDED — 판매자 자가복구 불가)와 해제(SUSPENDED→HIDDEN, 판매자 재게시)다.
 */
public interface OperatorHouseService {

	/** 운영자 대행 고택 등록(HOUSE_PROXY_MNG) — 대상 파트너(승인) 소유로 DRAFT 생성, 행위자=운영자. */
	HouseDetailResponse insert(OperatorHouseInsertRequest request, AuthUser operator);

	/** 전 판매자 고택 목록(HOUSE_VIEW). */
	PageResponse<OperatorHouseListItemResponse> list(OperatorHouseSearchRequest request);

	/** 상품 목록 필터 옵션(HOUSE_VIEW) — 상태/지역(시도)/검색 메타. 프론트 드롭다운 수동 하드코딩 제거용. */
	OperatorHouseFilterOptionsResponse filterOptions();

	/**
	 * 상세 drawer 조회(HOUSE_VIEW) — 헤더/수용인원/요일요금/객실(모달 데이터)/휴관일/판매자 aggregate 1회.
	 * from/to 미지정 시 [오늘(KST), 오늘+90일). 최대 92일, 역전/초과는 400. 미존재/삭제는 404.
	 */
	OperatorHouseDetailDrawerResponse detailDrawer(long houseSn, java.time.LocalDate from, java.time.LocalDate to);

	/** 고택 상세(HOUSE_VIEW — 소유권 무관). */
	HouseDetailResponse detail(long houseSn);

	/**
	 * 운영개시 사전점검(HOUSE_PROXY_MNG) — 상태변경 없이 발행 9조건 충족 여부 read-only 점검.
	 * owner 는 대상 고택에서 서버 해석(요청 prtnrSn 불신). 존재/소유권 실패는 404. DB write 없음.
	 */
	PublishCheckResponse publishCheck(long houseSn, AuthUser operator);

	/** 강제 게시중지(HOUSE_MODER) — SUSPENDED. 사유는 감사로그 기록. */
	void suspend(HouseSuspendRequest request, AuthUser operator);

	/** 임시조치 해제(HOUSE_MODER) — SUSPENDED→HIDDEN. */
	void release(HouseReleaseRequest request, AuthUser operator);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner 는 대상 고택에서 서버 해석, 행위자=운영자 =====

	/** 대행 고택 수정(HOUSE_PROXY_MNG). */
	void update(HouseUpdateRequest request, AuthUser operator);

	/** 대행 고택 상태변경(HOUSE_PROXY_MNG — DRAFT/ACTIVE/HIDDEN. ACTIVE 전이 시 발행검증. SUSPENDED 불가). */
	void changeStatus(HouseStatusChangeRequest request, AuthUser operator);

	/** 대행 고택 정책 수정(HOUSE_PROXY_MNG — 위저드 Step2). */
	void updatePolicy(HousePolicyUpdateRequest request, AuthUser operator);

	/** 대행 고택 결제수단 설정(HOUSE_PROXY_MNG — 위저드 Step2, 전체 교체). */
	void setPayMethods(HousePayMethodSetRequest request, AuthUser operator);

	/** 대행 고택 일자별 대표요금 설정(HOUSE_PROXY_MNG — 주중/금/토/성수기 4구간, 전체 교체). */
	void setDailyRate(HouseDailyRateSetRequest request, AuthUser operator);

	/** 대행 고택 정기휴관 설정(HOUSE_PROXY_MNG — 주간휴관요일·공휴일·명절 휴무, 전체 교체). */
	void setRegularClosure(HouseRegularClosureSetRequest request, AuthUser operator);

	/** 대행 고택 삭제(HOUSE_PROXY_MNG — soft delete + 하위 객실 동반, owner 서버해석). */
	void delete(HouseDeleteRequest request, AuthUser operator);

	// ===== 검수 전이(2026-07-06) — 상태별 전용 API(/status 와 분리). 이력=HOUSE_REVIEW_HSTRY =====

	/**
	 * 검수요청(HOUSE_PROXY_MNG) — DRAFT/HIDDEN → REVIEW_REQUESTED. 발행 9조건 통과 필수(미충족=409+failedConditions).
	 * 소스상태 불일치=409 HOUSE_REVIEW_STATE_INVALID.
	 */
	void reviewRequest(HouseReviewRequest request, AuthUser operator);

	/**
	 * 검수 승인(HOUSE_REVIEW) — REVIEW_REQUESTED → ACTIVE. 승인 시점 발행 9조건 재검증(표류 방지, 미충족=409+failedConditions).
	 * 소스상태 불일치=409.
	 */
	void reviewApprove(HouseReviewRequest request, AuthUser operator);

	/** 검수 반려(HOUSE_REVIEW) — REVIEW_REQUESTED → DRAFT. 반려사유 필수(이력 저장). 소스상태 불일치=409. */
	void reviewReject(HouseReviewRejectRequest request, AuthUser operator);
}
