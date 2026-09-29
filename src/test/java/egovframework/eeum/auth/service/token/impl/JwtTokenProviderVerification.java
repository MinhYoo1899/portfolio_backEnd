package egovframework.eeum.auth.service.token.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import egovframework.com.jwt.InvalidJwtException;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.IssuedRefreshToken;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.ParsedAccessToken;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.RefreshTokenClaims;
import egovframework.eeum.auth.vo.AuthUser;

/**
 * JWT 토큰 세대(generation) 라운드트립 검증 — 무효화 판정의 토대(codex SHOULD ③).
 * 발급 시 박은 gen 이 검증에서 그대로 복원되는지, refresh→access 타입 혼용이 거부되는지 확인한다.
 */
class JwtTokenProviderVerification {

	private final JwtTokenProvider provider = new JwtTokenProvider();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(provider, "secretKeyString", "0123456789012345678901234567890123456789");
		ReflectionTestUtils.setField(provider, "accessValiditySeconds", 1800L);
		ReflectionTestUtils.setField(provider, "refreshValiditySeconds", 1209600L);
	}

	private static AuthUser user() {
		return new AuthUser(42L, "u@eeum.kr", "홍길동", "MEMBER", "ACTIVE", List.of("CHNL_LOGIN_USER"));
	}

	@Test
	@DisplayName("access 토큰 — 발급 시 세대가 검증에서 그대로 복원")
	void accessGenerationRoundTrip() {
		String token = provider.issueAccessToken(user(), 7L);
		ParsedAccessToken parsed = provider.parseAccessToken(token);
		assertEquals(7L, parsed.generation());
		assertEquals(42L, parsed.principal().userSn());
		assertEquals(List.of("CHNL_LOGIN_USER"), parsed.principal().permissions());
	}

	@Test
	@DisplayName("refresh 토큰 — 세대/채널/jti 가 검증에서 그대로 복원")
	void refreshGenerationRoundTrip() {
		IssuedRefreshToken issued = provider.issueRefreshToken(42L, "USER", 9L);
		RefreshTokenClaims claims = provider.parseRefreshToken(issued.token());
		assertEquals(9L, claims.generation());
		assertEquals(42L, claims.userSn());
		assertEquals("USER", claims.chnlCd());
		assertNotNull(claims.jti());
	}

	@Test
	@DisplayName("타입 혼용 거부 — refresh 토큰을 access 로 파싱하면 InvalidJwtException")
	void refreshTokenRejectedAsAccess() {
		IssuedRefreshToken issued = provider.issueRefreshToken(42L, "USER", 1L);
		assertThrows(InvalidJwtException.class, () -> provider.parseAccessToken(issued.token()));
	}

	@Test
	@DisplayName("타입 혼용 거부 — access 토큰을 refresh 로 파싱하면 InvalidJwtException")
	void accessTokenRejectedAsRefresh() {
		String access = provider.issueAccessToken(user(), 1L);
		assertThrows(InvalidJwtException.class, () -> provider.parseRefreshToken(access));
	}
}
