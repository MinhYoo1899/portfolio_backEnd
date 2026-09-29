package egovframework.eeum.auth.service.auth.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import egovframework.eeum.auth.service.auth.dao.AuthMapper;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.AuthTokenIssuer;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.RefreshTokenClaims;
import egovframework.eeum.auth.vo.AuthAccountVO;
import egovframework.eeum.auth.web.dto.LoginRequest;

/**
 * 인증 서비스 세대(generation) 전달 검증 — 핵심 회귀 방어(codex 3차 SHOULD).
 * refresh 는 검증된 토큰의 세대를 <b>상속</b>(issuer 가 현재 세대를 재-read 하면 안 됨)하고,
 * 신규 로그인은 <b>현재 세대</b>를 읽어 발급하는지 mock 으로 고정한다.
 */
class AuthServiceVerification {

	private final AuthMapper authMapper = mock(AuthMapper.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
	private final TokenSessionStore tokenSessionStore = mock(TokenSessionStore.class);
	private final AuthRevocationStore authRevocationStore = mock(AuthRevocationStore.class);
	private final LoginAttemptGuard loginAttemptGuard = mock(LoginAttemptGuard.class);
	private final AuthTokenIssuer authTokenIssuer = mock(AuthTokenIssuer.class);

	private final AuthServiceImpl service = new AuthServiceImpl(
			authMapper, passwordEncoder, tokenProvider, tokenSessionStore,
			authRevocationStore, loginAttemptGuard, authTokenIssuer);

	private static AuthAccountVO account() {
		AuthAccountVO a = new AuthAccountVO();
		a.setUserSn(42L);
		a.setEmlAddr("u@eeum.kr");
		a.setEnpswd("ENC");
		a.setAcntStts("ACTIVE");
		a.setUserNm("홍길동");
		a.setUserTypeCd("MEMBER");
		return a;
	}

	@Test
	@DisplayName("refresh — 검증된 토큰의 세대를 상속해 발급(현재 세대를 재-read 하지 않음)")
	void refreshInheritsTokenGeneration() {
		AuthAccountVO account = account();
		List<String> perms = List.of("CHNL_LOGIN_USER");
		// 토큰 세대=3, 현재 세대=2 → 3>=2 라 통과. 상속이면 새 토큰 gen=3, 잘못 재-read 하면 gen=2 가 되어 구분됨.
		when(tokenProvider.parseRefreshToken("rt")).thenReturn(new RefreshTokenClaims(42L, "jti-1", "USER", 3L));
		when(authRevocationStore.currentGeneration(42L)).thenReturn(2L);
		when(authMapper.selectActiveAccountByUserSn(42L)).thenReturn(account);
		when(authMapper.selectAuthCdsByUserSn(42L)).thenReturn(perms);

		service.refresh("rt", "USER");

		// 멱등 회전(grace) — 검증된 토큰의 jti·세대를 그대로 넘겨 회전(issue 가 아닌 rotate 사용)
		verify(authTokenIssuer).rotate(eq(account), eq(perms), eq("USER"), eq(3L), eq("jti-1"));
	}

	@Test
	@DisplayName("login — 현재 세대를 읽어 발급")
	void loginUsesCurrentGeneration() {
		AuthAccountVO account = account();
		List<String> perms = List.of("CHNL_LOGIN_USER");
		when(authMapper.selectActiveAccountByEmlAddr("u@eeum.kr")).thenReturn(account);
		when(passwordEncoder.matches("pw", "ENC")).thenReturn(true);
		when(authMapper.selectAuthCdsByUserSn(42L)).thenReturn(perms);
		when(authRevocationStore.currentGeneration(42L)).thenReturn(5L);

		service.login(new LoginRequest("u@eeum.kr", "pw", "WEB", "USER"), "1.1.1.1", "ua");

		verify(authTokenIssuer).issue(eq(account), eq(perms), eq("USER"), eq(5L));
	}
}
