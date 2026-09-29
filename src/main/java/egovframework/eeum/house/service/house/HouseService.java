package egovframework.eeum.house.service.house;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.house.web.dto.HouseDetailResponse;
import egovframework.eeum.house.web.dto.HouseInsertRequest;
import egovframework.eeum.house.web.dto.HouseListItemResponse;
import egovframework.eeum.house.web.dto.HousePayMethodSetRequest;
import egovframework.eeum.house.web.dto.HousePolicyUpdateRequest;
import egovframework.eeum.house.web.dto.HouseSearchRequest;
import egovframework.eeum.house.web.dto.HouseStatusChangeRequest;
import egovframework.eeum.house.web.dto.HouseUpdateRequest;

/**
 * 고택 서비스(판매자) — 본인 소유 고택 등록/수정/삭제/조회/상태변경.
 *
 * <p>소유자(PRTNR_SN)는 인증 사용자로부터 해석(요청 body 불신). 모든 변경은 본인 소유에만 적용.
 * 운영자 조회·임시조치(SUSPENDED)는 후속 슬라이스에서 별도 제공.
 */
public interface HouseService {

	/** 고택 등록(판매자) — DRAFT 상태로 생성. */
	HouseDetailResponse insert(HouseInsertRequest request, AuthUser partner);

	/** 본인 고택 목록(오프셋 페이징). */
	PageResponse<HouseListItemResponse> list(HouseSearchRequest request, AuthUser partner);

	/** 본인 고택 상세. */
	HouseDetailResponse detail(long houseSn, AuthUser partner);

	/** 수정(본인 소유). */
	void update(HouseUpdateRequest request, AuthUser partner);

	/** 상태변경(본인 소유 — DRAFT/ACTIVE/HIDDEN. SUSPENDED 복구 불가). ACTIVE 전이 시 발행검증. */
	void changeStatus(HouseStatusChangeRequest request, AuthUser partner);

	/** 정책 수정(위저드 Step2 — 추가금/체크인·아웃/예약확정/취소환불·대중교통, 본인 소유). */
	void updatePolicy(HousePolicyUpdateRequest request, AuthUser partner);

	/** 결제수단 설정(위저드 Step2 — 전체 교체, 본인 소유). */
	void setPayMethods(HousePayMethodSetRequest request, AuthUser partner);

	/** 삭제(본인 소유, soft). */
	void delete(long houseSn, AuthUser partner);
}
