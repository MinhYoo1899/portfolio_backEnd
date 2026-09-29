package egovframework.eeum.auth.service.auth.impl;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import egovframework.com.jwt.InvalidJwtException;
import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.service.auth.dao.AuthMapper;
import egovframework.eeum.auth.vo.AuthAccountVO;
import egovframework.eeum.auth.vo.LoginLogInfoVO;
import egovframework.eeum.auth.service.auth.AuthService;
import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.AuthUserInfo;
import egovframework.eeum.auth.web.dto.LoginRequest;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.AuthTokenIssuer;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider;
import egovframework.eeum.base.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증 서비스 구현.
 *
 * <p>의도적으로 {@code @Transactional} 미사용 — 로그인 실패 시에도 LOGIN_LOG_INFO(실패 로그)는
 * 반드시 남아야 하므로(예외 롤백 금지) 각 기록은 개별 커밋한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private static final String LGN_TYPE_EMAIL = "EMAIL";
	private static final String RSLT_SUCCESS = "SUCCESS";
	private static final String RSLT_FAIL = "FAIL";

	/** 로그인 채널 자격 권한코드 접두사 — V5 시드(CHNL_LOGIN_USER/PARTNER/OPERATOR)와 짝 */
	private static final String AUTH_CHNL_LOGIN_PREFIX = "CHNL_LOGIN_";

	/** 로그인 허용 계정상태 — PENDING 은 승인대기(로그인 허용 + 상태 반환, 확정 정책) */
	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_PENDING = "PENDING";
	private static final String STTS_SUSPENDED = "SUSPENDED";
	private static final String STTS_REJECTED = "REJECTED";

	private final AuthMapper authMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;
	private final TokenSessionStore tokenSessionStore;
	private final AuthRevocationStore authRevocationStore;
	private final LoginAttemptGuard loginAttemptGuard;
	private final AuthTokenIssuer authTokenIssuer;

	@Override
	public AuthTokenResponse login(LoginRequest request, String cntnIp, String userAgnt) {
		// 무차별 대입 방어 — 임계 초과 시 차단(열거 방지 위해 자격증명 오류와 동일 응답), Redis 장애 시 503(fail-closed)
		loginAttemptGuard.assertNotBlocked(request.emlAddr(), cntnIp);
		try {
			AuthTokenResponse response = doLogin(request, cntnIp, userAgnt);
			loginAttemptGuard.recordSuccess(request.emlAddr(), cntnIp); // 성공 — 계정+IP 실패 카운터 리셋
			return response;
		} catch (BusinessException e) {
			// 모든 실패(미존재/비번/상태/채널 불허)를 동일하게 카운트 — 열거 방어와 정합
			loginAttemptGuard.recordFailure(request.emlAddr(), cntnIp);
			throw e;
		}
	}

	private AuthTokenResponse doLogin(LoginRequest request, String cntnIp, String userAgnt) {
		AuthAccountVO account = authMapper.selectActiveAccountByEmlAddr(request.emlAddr());

		// 미존재/비밀번호 불일치/소셜전용(ENPSWD NULL)/탈퇴 → 단일 코드(열거 방지)
		if (account == null || account.getEnpswd() == null
				|| !passwordEncoder.matches(request.pswd(), account.getEnpswd())) {
			recordHistory(account, request.emlAddr(), RSLT_FAIL, "잘못된 인증정보", cntnIp, userAgnt);
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		switch (account.getAcntStts() == null ? "" : account.getAcntStts()) {
			case STTS_ACTIVE, STTS_PENDING -> { /* 로그인 허용 */ }
			case STTS_SUSPENDED -> {
				recordHistory(account, request.emlAddr(), RSLT_FAIL, "정지 계정", cntnIp, userAgnt);
				throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_SUSPENDED);
			}
			case STTS_REJECTED -> {
				recordHistory(account, request.emlAddr(), RSLT_FAIL, "반려 계정", cntnIp, userAgnt);
				throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_REJECTED);
			}
			default -> {
				// WITHDRAWN 등 — 열거 방지 위해 인증정보 오류와 동일 응답
				recordHistory(account, request.emlAddr(), RSLT_FAIL, "비활성 계정상태: " + account.getAcntStts(), cntnIp, userAgnt);
				throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
			}
		}

		// 채널 자격 — 계정 권한에 CHNL_LOGIN_<채널> 없으면 거부. 응답은 자격증명 오류와 동일
		// (불허 사실 비노출: "비밀번호는 맞았다"는 정보가 새는 것 방지 — 실제 사유는 LOGIN_LOG_INFO 에만)
		List<String> permissions = authMapper.selectAuthCdsByUserSn(account.getUserSn());
		if (!permissions.contains(AUTH_CHNL_LOGIN_PREFIX + request.chnlCd())) {
			recordHistory(account, request.emlAddr(), RSLT_FAIL, "채널 불허: " + request.chnlCd(), cntnIp, userAgnt);
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		// 신규 로그인 — 현재 세대로 발급(최신 세대 부여).
		// (수용된 좁은 경합: 비밀번호 변경 트랜잭션의 Redis bump 가 DB 커밋 전에 보이고, 그 찰나 옛 비밀번호 로그인이
		//  커밋 전 DB 를 읽어 최신 세대 토큰을 받을 수 있음. dev 단일 노드·옛 비밀번호 인지 전제로 위험 제한적 — codex MUST ② 수용.
		//  엄밀 직렬화가 필요해지면 DB-backed generation 으로 격상.)
		long generation = authRevocationStore.currentGeneration(account.getUserSn());
		AuthTokenResponse response = authTokenIssuer.issue(account, permissions, request.chnlCd(), generation);

		authMapper.updateLastLgnDt(account.getUserSn());
		recordHistory(account, request.emlAddr(), RSLT_SUCCESS, null, cntnIp, userAgnt);
		return response;
	}

	@Override
	public AuthTokenResponse refresh(String refreshToken, String chnlCd) {
		JwtTokenProvider.RefreshTokenClaims claims;
		try {
			claims = tokenProvider.parseRefreshToken(refreshToken);
		} catch (InvalidJwtException e) {
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID, AuthErrorCode.AUTH_REFRESH_INVALID.defaultMessage(), e);
		}

		// 채널 일치 — 소비(consume) 전에 검사해 불일치 요청이 정상 세션을 파괴하지 못하게 한다.
		// 구버전 토큰(chnl 클레임 없음)도 거부 → 재로그인 유도.
		if (claims.chnlCd() == null || !claims.chnlCd().equals(chnlCd)) {
			log.warn("[AUTH] refresh channel mismatch. userSn={}, token chnl={}, requested={}",
					claims.userSn(), claims.chnlCd(), chnlCd);
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID);
		}

		// 토큰 세대 검증(refresh 회전 경합 방어) — 비밀번호 변경/탈퇴로 세대가 오른 뒤의 옛 세대 refresh 를 거부한다.
		// 단순 reject(소비/전체회수 안 함) — 옛 세대 토큰은 그 자체로 무효라 추가 폐기가 불필요하고,
		// 공격자가 옛 refresh 하나로 사용자의 정상 재로그인 세션까지 계속 날리는 DoS 를 막는다(codex 3차 SHOULD).
		if (claims.generation() < authRevocationStore.currentGeneration(claims.userSn())) {
			log.warn("[AUTH] refresh generation revoked. userSn={}, token gen={}", claims.userSn(), claims.generation());
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID);
		}

		// (회전 레이스 방어) 세션 소비/회전은 모든 검증 통과 후 마지막에 멱등 처리한다(아래 authTokenIssuer.rotate).
		AuthAccountVO account = authMapper.selectActiveAccountByUserSn(claims.userSn());
		if (account == null
				|| !(STTS_ACTIVE.equals(account.getAcntStts()) || STTS_PENDING.equals(account.getAcntStts()))) {
			tokenSessionStore.revokeAll(claims.userSn());
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID);
		}

		// 채널 자격 재검증 — 자격이 회수된 계정이 기존 refresh 로 14일간 재발급을 잇는 것 차단
		// (codex 교차검토 필수수정 — 회수 시 노출은 최대 기존 access 만료(30분)로 축소)
		List<String> permissions = authMapper.selectAuthCdsByUserSn(account.getUserSn());
		if (!permissions.contains(AUTH_CHNL_LOGIN_PREFIX + chnlCd)) {
			log.warn("[AUTH] refresh channel authority revoked. userSn={}, chnl={} — revoking all sessions",
					claims.userSn(), chnlCd);
			tokenSessionStore.revokeAll(claims.userSn());
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID);
		}

		// 세대 상속(MUST ①) — 재발급은 검증된 refresh 의 세대를 그대로 박는다. 재발급 도중 비밀번호 변경 bump 가
		// 끼어들면 새 토큰(옛 세대)이 필터/refresh 에서 즉시 거부되어, 옛 세션이 새 세대로 승격되지 않는다.
		// 멱등 회전(grace) — 빠른 새로고침 등 회전 레이스는 직전 토큰 재서명으로 흡수, grace 창 밖 재사용만 회수+401.
		return authTokenIssuer.rotate(account, permissions, chnlCd, claims.generation(), claims.jti());
	}

	@Override
	public void logout(String refreshToken, Long authenticatedUserSn) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			try {
				JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken);
				tokenSessionStore.revoke(claims.userSn(), claims.jti());
				return;
			} catch (InvalidJwtException e) {
				// 무효 토큰 — 멱등 처리(아래 access 기준 fallback)
			}
		}
		if (authenticatedUserSn != null) {
			tokenSessionStore.revokeAll(authenticatedUserSn);
		}
	}

	private void recordHistory(AuthAccountVO account, String lgnId, String prcsRslt, String failRsn,
			String cntnIp, String userAgnt) {
		try {
			authMapper.insertLoginLogInfo(LoginLogInfoVO.builder()
					.userSn(account == null ? null : account.getUserSn())
					.lgnId(lgnId)
					.lgnType(LGN_TYPE_EMAIL)
					.prcsRslt(prcsRslt)
					.failRsn(failRsn)
					.cntnIp(cntnIp)
					.userAgnt(userAgnt)
					.build());
		} catch (RuntimeException e) {
			// 이력 기록 실패가 로그인 자체를 막으면 안 됨 — 로그만 남긴다
			log.error("[AUTH] LOGIN_LOG_INFO insert failed. lgnId={}", lgnId, e);
		}
	}
}
