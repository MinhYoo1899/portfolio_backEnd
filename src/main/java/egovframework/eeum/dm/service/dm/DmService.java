package egovframework.eeum.dm.service.dm;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.dm.web.dto.DmCreateRequest;
import egovframework.eeum.dm.web.dto.DmResponse;
import egovframework.eeum.dm.web.dto.DmSearchRequest;

/**
 * DM 서비스 — 목록/상세/발송(즉시·예약·임시저장)/삭제 + 예약 자동발송(cron). 실 전송은 스텁(벤더 미선정).
 */
public interface DmService {

	/** 목록(상태/채널/제목 필터 + 페이지네이션). */
	PageResponse<DmResponse> list(DmSearchRequest request);

	/** 단건 조회. 없으면 DM_NOT_FOUND. */
	DmResponse get(long dmId);

	/** 발송/예약/임시저장 — saveAsDraft/sendTiming 조합으로 상태 산정. 즉시발송 시 발송(스텁). */
	DmResponse create(DmCreateRequest request, AuthUser user);

	/** soft delete. 없으면 DM_NOT_FOUND. */
	void delete(long dmId, AuthUser user);

	/** 예약 DM 자동발송(cron). 발송 건수 반환. */
	int promoteScheduled();
}
