package egovframework.eeum.auth.service.token.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import egovframework.com.jwt.InvalidJwtException;
import egovframework.eeum.auth.vo.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/**
 * 이음 JWT 발급/검증 (access + refresh).
 *
 * <p>eGov {@code EgovJwtTokenUtil}은 데모 LoginVO 클레임에 결합되어 있어,
 * 우리 도메인 클레임(USER_SN/권한코드)용으로 별도 제공자를 둔다. (SSO 분리 시 이 클래스만 IdP 로 대체 — TokenVerifier 경계)
 *
 * <ul>
 *   <li>access: typ=ACCESS, sub=userSn, 표시용 클레임 + 권한코드 목록</li>
 *   <li>refresh: typ=REFRESH, sub=userSn, jti(회전/회수 단위 — Redis TokenSessionStore 와 짝)</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

	public static final String TOKEN_TYPE_CLAIM = "typ";
	public static final String TOKEN_TYPE_ACCESS = "ACCESS";
	public static final String TOKEN_TYPE_REFRESH = "REFRESH";

	private static final String CLAIM_EML_ADDR = "emlAddr";
	private static final String CLAIM_USER_NM = "userNm";
	private static final String CLAIM_USER_TYPE_CD = "userTypeCd";
	private static final String CLAIM_ACNT_STTS = "acntStts";
	private static final String CLAIM_AUTH = "auth";
	/** refresh 전용 — 원 로그인 채널(USER/PARTNER/OPERATOR). 재발급 시 채널 일치 검증에 사용 */
	private static final String CLAIM_CHNL_CD = "chnl";
	/** 토큰 세대 — 발급 시점의 사용자 세대. 비밀번호 변경·탈퇴로 세대가 오르면 옛 세대 토큰은 거부됨(무효화). */
	private static final String CLAIM_GEN = "gen";

	/** HS256 최소 키 길이(256비트). 미만이면 토큰 위조에 취약 → 기동 거부. */
	private static final int MIN_SECRET_BYTES = 32;

	@Value("${Globals.jwt.secret}")
	private String secretKeyString;

	@Value("${Globals.jwt.accessValiditySeconds:1800}")
	private long accessValiditySeconds;

	@Value("${Globals.jwt.refreshValiditySeconds:1209600}")
	private long refreshValiditySeconds;

	/**
	 * 기동 시 JWT 서명키 검증(fail-fast, 보안 자가점검+codex) — 없거나 32바이트 미만이면 기동 거부.
	 * 운영은 EGOV_JWT_SECRET 환경변수로 32바이트+ 무작위 키를 주입해야 한다(약한 공개 기본값으로 토큰 위조되는 사고 차단).
	 */
	@PostConstruct
	void validateSecret() {
		int bytes = secretKeyString == null ? 0 : secretKeyString.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT 서명키(Globals.jwt.secret)가 없거나 너무 짧습니다(" + bytes + "바이트 < " + MIN_SECRET_BYTES
							+ "). 운영은 EGOV_JWT_SECRET 환경변수로 32바이트+ 무작위 키를 주입하세요.");
		}
	}

	private SecretKey secretKey() {
		return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
	}

	public Duration accessValidity() {
		return Duration.ofSeconds(accessValiditySeconds);
	}

	public Duration refreshValidity() {
		return Duration.ofSeconds(refreshValiditySeconds);
	}

	/** access 토큰 발급 — generation 은 발급 시점의 사용자 세대(무효화 판정용). */
	public String issueAccessToken(AuthUser user, long generation) {
		Map<String, Object> claims = new HashMap<>();
		claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS);
		claims.put(CLAIM_EML_ADDR, user.emlAddr());
		claims.put(CLAIM_USER_NM, user.userNm());
		claims.put(CLAIM_USER_TYPE_CD, user.userTypeCd());
		claims.put(CLAIM_ACNT_STTS, user.acntStts());
		claims.put(CLAIM_AUTH, user.permissions());
		claims.put(CLAIM_GEN, generation);

		long now = System.currentTimeMillis();
		return Jwts.builder()
				.claims(claims)
				.subject(String.valueOf(user.userSn()))
				.issuedAt(new Date(now))
				.expiration(new Date(now + accessValiditySeconds * 1000))
				.signWith(secretKey())
				.compact();
	}

	/** refresh 토큰 발급 — jti 가 세션 회수 단위, chnlCd 는 재발급 채널 일치 검증용, generation 은 무효화 판정용. */
	public IssuedRefreshToken issueRefreshToken(long userSn, String chnlCd, long generation) {
		String jti = UUID.randomUUID().toString();
		long now = System.currentTimeMillis();
		long expEpochMs = now + refreshValiditySeconds * 1000;
		String token = signRefreshToken(userSn, chnlCd, generation, jti, expEpochMs);
		return new IssuedRefreshToken(token, jti, expEpochMs);
	}

	/**
	 * 주어진 jti·만료로 refresh 토큰을 재서명한다(grace 멱등 회전용 — 직전 회전에서 발급한 동일 논리 토큰 복원).
	 * iat 은 현재시각이라 바이트 동일하진 않지만 jti/exp/chnl/gen 이 같아 동일 세션으로 취급된다.
	 */
	public String signRefreshToken(long userSn, String chnlCd, long generation, String jti, long expEpochMs) {
		long now = System.currentTimeMillis();
		return Jwts.builder()
				.claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
				.claim(CLAIM_CHNL_CD, chnlCd)
				.claim(CLAIM_GEN, generation)
				.subject(String.valueOf(userSn))
				.id(jti)
				.issuedAt(new Date(now))
				.expiration(new Date(expEpochMs))
				.signWith(secretKey())
				.compact();
	}

	/**
	 * access 토큰 검증 + 프린시펄·세대 복원. refresh 토큰을 access 로 쓰는 시도는 거부.
	 * 세대(gen)는 무효화 판정(AuthRevocationStore) 비교에 쓰인다. (구버전 토큰=gen 없음→0)
	 */
	public ParsedAccessToken parseAccessToken(String token) throws InvalidJwtException {
		Claims claims = parse(token);
		requireType(claims, TOKEN_TYPE_ACCESS);
		try {
			List<String> permissions = toStringList(claims.get(CLAIM_AUTH));
			AuthUser user = new AuthUser(
					Long.valueOf(claims.getSubject()),
					asString(claims.get(CLAIM_EML_ADDR)),
					asString(claims.get(CLAIM_USER_NM)),
					asString(claims.get(CLAIM_USER_TYPE_CD)),
					asString(claims.get(CLAIM_ACNT_STTS)),
					permissions);
			return new ParsedAccessToken(user, asLong(claims.get(CLAIM_GEN)));
		} catch (NumberFormatException e) {
			throw new InvalidJwtException("Invalid subject in access token");
		}
	}

	/** refresh 토큰 검증 — (userSn, jti, chnlCd) 반환. chnlCd 는 구버전 토큰이면 null(채널 검증에서 거부됨). */
	public RefreshTokenClaims parseRefreshToken(String token) throws InvalidJwtException {
		Claims claims = parse(token);
		requireType(claims, TOKEN_TYPE_REFRESH);
		if (claims.getId() == null) {
			throw new InvalidJwtException("Missing jti in refresh token");
		}
		try {
			return new RefreshTokenClaims(Long.parseLong(claims.getSubject()), claims.getId(),
					asString(claims.get(CLAIM_CHNL_CD)), asLong(claims.get(CLAIM_GEN)));
		} catch (NumberFormatException e) {
			throw new InvalidJwtException("Invalid subject in refresh token");
		}
	}

	private Claims parse(String token) throws InvalidJwtException {
		try {
			return Jwts.parser()
					.verifyWith(secretKey())
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			throw new InvalidJwtException("Unable to verify JWT token: " + e.getMessage());
		}
	}

	private void requireType(Claims claims, String expected) throws InvalidJwtException {
		Object type = claims.get(TOKEN_TYPE_CLAIM);
		if (!expected.equals(type)) {
			throw new InvalidJwtException("Unexpected token type: " + type);
		}
	}

	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}

	/** 숫자 클레임 → long. 없거나(구버전 토큰) 형식 오류면 0. */
	private static long asLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value == null) {
			return 0L;
		}
		try {
			return Long.parseLong(value.toString());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static List<String> toStringList(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream().map(String::valueOf).toList();
	}

	/** access 토큰에서 복원한 (프린시펄, 토큰 세대). 세대는 무효화 판정용(현재 세대 미만이면 거부). */
	public record ParsedAccessToken(AuthUser principal, long generation) {
	}

	/** 발급된 refresh 토큰 + 세션 키(jti) + 만료(epoch ms — grace 회전 시 successor 복원용). */
	public record IssuedRefreshToken(String token, String jti, long expEpochMs) {
	}

	/** refresh 토큰에서 복원한 (사용자, 세션, 원 로그인 채널, 토큰 세대) 식별. */
	public record RefreshTokenClaims(long userSn, String jti, String chnlCd, long generation) {
	}
}
