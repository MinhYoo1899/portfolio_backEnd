package egovframework.eeum.house.service.room;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.RoomBeddingResponse;
import egovframework.eeum.house.web.dto.RoomBeddingSetRequest;
import egovframework.eeum.house.web.dto.RoomInsertRequest;
import egovframework.eeum.house.web.dto.RoomResponse;
import egovframework.eeum.house.web.dto.RoomStatusChangeRequest;
import egovframework.eeum.house.web.dto.RoomUpdateRequest;

/**
 * 객실 서비스 — 판매자(파트너 본인) + 운영자 대행(HOUSE_PROXY_MNG) 양쪽 경로.
 *
 * <p>소유권은 부모 고택(PRTNR_SN)으로 판정. 판매자는 토큰에서, 운영자는 대상 고택/객실에서 서버가 해석(요청에 prtnrSn 없음).
 * 코어 로직은 (ownerPrtnrSn, actorUserSn) 으로 공유하고 진입점만 owner 해석을 달리한다.
 */
public interface RoomService {

	// ===================== 판매자(파트너 본인) =====================

	/** 객실 등록(부모 고택 본인 소유) — DRAFT 로 생성. */
	RoomResponse insert(RoomInsertRequest request, AuthUser partner);

	/** 고택별 객실 목록(본인 소유 고택). */
	List<RoomResponse> listByHouse(long houseSn, AuthUser partner);

	/** 객실 상세(본인 소유). */
	RoomResponse detail(long roomSn, AuthUser partner);

	/** 수정(본인 소유). */
	void update(RoomUpdateRequest request, AuthUser partner);

	/** 상태변경(본인 소유 — DRAFT/ACTIVE/HIDDEN. SUSPENDED 복구 불가). */
	void changeStatus(RoomStatusChangeRequest request, AuthUser partner);

	/** 삭제(본인 소유, soft). */
	void delete(long roomSn, AuthUser partner);

	/** 침구 설정(본인 소유, 교체). */
	List<RoomBeddingResponse> setBeddings(RoomBeddingSetRequest request, AuthUser partner);

	// ============ 운영자 대행(HOUSE_PROXY_MNG) — owner=서버 해석, 행위자=운영자 ============

	/** 객실 등록(운영자 대행) — 대상 고택 owner 서버 해석. */
	RoomResponse insertAsOperator(RoomInsertRequest request, AuthUser operator);

	/** 고택별 객실 목록(운영자 대행). */
	List<RoomResponse> listByHouseAsOperator(long houseSn, AuthUser operator);

	/** 객실 상세(운영자 대행). */
	RoomResponse detailAsOperator(long roomSn, AuthUser operator);

	/** 수정(운영자 대행). */
	void updateAsOperator(RoomUpdateRequest request, AuthUser operator);

	/** 상태변경(운영자 대행). */
	void changeStatusAsOperator(RoomStatusChangeRequest request, AuthUser operator);

	/** 삭제(운영자 대행, soft). */
	void deleteAsOperator(long roomSn, AuthUser operator);

	/** 침구 설정(운영자 대행, 교체). */
	List<RoomBeddingResponse> setBeddingsAsOperator(RoomBeddingSetRequest request, AuthUser operator);
}
