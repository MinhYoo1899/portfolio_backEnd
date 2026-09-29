package egovframework.eeum.auth.service.token;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.auth.service.token.TokenSessionStore.RefreshDescriptor;
import egovframework.eeum.auth.service.token.TokenSessionStore.RotationResult;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.IssuedRefreshToken;
import egovframework.eeum.auth.vo.AuthAccountVO;
import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.auth.web.dto.AuthTokenResponse;
import egovframework.eeum.auth.web.dto.AuthUserInfo;
import egovframework.eeum.base.exception.BusinessException;
import lombok.RequiredArgsConstructor;

/**
 * 우리 자체 토큰(access/refresh) 발급 — 이메일 로그인·소셜 로그인이 공유하는 단일 진실원.
 * (refresh 세션은 Redis 회전. chnlCd 는 재발급 채널 일치 검증용 클레임.)
 */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {

	private final JwtTokenProvider tokenProvider;
	private final TokenSessionStore tokenSessionStore;

	/** refresh 회전 grace window(초) — 빠른 새로고침 등 회전 레이스 허용창. codex 권고 10초(운영 데이터로 5초까지 조정 가능). */
	@Value("${Globals.jwt.refreshGraceSeconds:10}")
	private long refreshGraceSeconds;

	/**
	 * access/refresh 발급 + refresh 세션 저장. 권한은 호출부가 채널 검증에 쓴 것을 재사용(이중 조회 방지).
	 *
	 * <p>generation 은 토큰에 박을 세대값으로, <b>호출자가 명시 전달</b>한다(issuer 가 숨어서 다시 읽지 않음 — codex MUST):
	 * 신규 로그인/소셜은 {@code currentGeneration} 을, refresh 재발급은 <b>검증된 토큰의 세대를 상속</b>해 전달한다.
	 * 후자가 핵심 — 재발급 중 비밀번호 변경 bump 가 끼어들어도 새 토큰이 옛 세대라 즉시 거부되어, 옛 세션이 새 세대로 "승격"되지 않는다.
	 */
	public AuthTokenResponse issue(AuthAccountVO account, List<String> permissions, String chnlCd, long generation) {
		AuthUser user = new AuthUser(
				account.getUserSn(),
				account.getEmlAddr(),
				account.getUserNm(),
				account.getUserTypeCd(),
				account.getAcntStts(),
				permissions);

		String accessToken = tokenProvider.issueAccessToken(user, generation);
		JwtTokenProvider.IssuedRefreshToken refresh =
				tokenProvider.issueRefreshToken(account.getUserSn(), chnlCd, generation);
		tokenSessionStore.save(account.getUserSn(), refresh.jti(), tokenProvider.refreshValidity());

		return new AuthTokenResponse(
				accessToken,
				tokenProvider.accessValidity().toSeconds(),
				refresh.token(),
				AuthUserInfo.from(user));
	}

	/**
	 * refresh 회전(멱등 grace) — 검증된 refresh 의 후속 토큰을 발급한다. 호출 전 채널/세대/계정/권한 검증이 끝나 있어야 한다.
	 *
	 * <ul>
	 *   <li>ROTATED: 정상 회전 — 새로 발급한 successor 반환</li>
	 *   <li>GRACE: 회전 레이스(빠른 새로고침 등) — 직전 successor 를 재서명해 반환(재회전 없음 → 정상 세션 유지)</li>
	 *   <li>REUSE: grace 창 밖 재사용 — 회수 후 401(탈취 방어 유지)</li>
	 * </ul>
	 * generation 은 검증된 토큰의 세대를 상속(재발급 중 bump 가 끼어들어도 옛 세대로 즉시 거부).
	 */
	public AuthTokenResponse rotate(AuthAccountVO account, List<String> permissions, String chnlCd, long generation,
			String oldJti) {
		AuthUser user = new AuthUser(
				account.getUserSn(),
				account.getEmlAddr(),
				account.getUserNm(),
				account.getUserTypeCd(),
				account.getAcntStts(),
				permissions);

		// successor 후보를 먼저 발급(원자 회전에서 ROTATED 면 활성화, GRACE 면 미사용·폐기)
		IssuedRefreshToken successor = tokenProvider.issueRefreshToken(account.getUserSn(), chnlCd, generation);
		RefreshDescriptor desc = new RefreshDescriptor(successor.jti(), chnlCd, generation, successor.expEpochMs());
		RotationResult result = tokenSessionStore.rotateOrGrace(account.getUserSn(), oldJti, desc,
				tokenProvider.refreshValidity(), Duration.ofSeconds(refreshGraceSeconds));

		String refreshToken = switch (result.outcome()) {
			case ROTATED -> successor.token();
			case GRACE -> {
				RefreshDescriptor s = result.successor();
				yield tokenProvider.signRefreshToken(account.getUserSn(), s.chnlCd(), s.generation(), s.jti(),
						s.refreshExpEpochMs());
			}
			case REUSE -> null;
		};
		if (refreshToken == null) {
			tokenSessionStore.revokeAll(account.getUserSn()); // grace 창 밖 재사용 — 방어적 회수
			throw new BusinessException(AuthErrorCode.AUTH_REFRESH_INVALID);
		}

		String accessToken = tokenProvider.issueAccessToken(user, generation);
		return new AuthTokenResponse(accessToken, tokenProvider.accessValidity().toSeconds(), refreshToken,
				AuthUserInfo.from(user));
	}
}
