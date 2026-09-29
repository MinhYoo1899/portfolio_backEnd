package egovframework.eeum.auth.service.auth;

import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.LoginRequest;

/**
 * 인증 서비스 — 로그인/재발급(회전)/로그아웃.
 */
public interface AuthService {

	/**
	 * 이메일+비밀번호 로그인. 성공 시 access+refresh 발급, LOGIN_LOG_INFO 기록, LAST_LGN_DT 갱신.
	 * PENDING(파트너 승인대기)도 로그인 허용 — 응답의 acntStts 로 프론트가 분기(확정 정책).
	 * 채널 자격: 계정 권한에 CHNL_LOGIN_&lt;chnlCd&gt; 없으면 거부 — 응답은 자격증명 오류와
	 * 동일(불허 사실 비노출, 실제 사유는 LOGIN_LOG_INFO 에만. 사용자 확정 정책 2026-06-12).
	 */
	AuthTokenResponse login(LoginRequest request, String cntnIp, String userAgnt);

	/**
	 * refresh 회전 재발급. 1회 소비 후 새 쌍 발급.
	 * 소비 실패(재사용/만료/회수)면 해당 사용자 전체 세션 회수 후 401(AUTH_REFRESH_INVALID).
	 * 채널 검증: 토큰의 원 로그인 채널과 {@code chnlCd} 불일치 시 거부(소비 전 — 정상 세션 보존),
	 * 채널 자격(CHNL_LOGIN_*)이 회수된 계정은 전체 세션 회수 후 거부 (codex 교차검토 필수수정).
	 */
	AuthTokenResponse refresh(String refreshToken, String chnlCd);

	/**
	 * 로그아웃 — refresh 세션 회수. refresh 가 없으면 access 기준 사용자의 전체 세션 회수.
	 * 멱등(이미 회수된 토큰이어도 성공 응답).
	 */
	void logout(String refreshToken, Long authenticatedUserSn);
}
