package egovframework.eeum.account.service.operator;

/**
 * 운영자 설정토큰 저장소 — 1회성 비밀번호 설정/초기화 토큰(Redis, HMAC 저장·TTL·1회성).
 *
 * <p>보안(이메일 인증 챌린지와 동일 원칙): 평문 토큰은 저장/로그 금지 — 해시(HMAC)만 저장한다.
 * 토큰은 발급 응답에 1회만 노출되고, 소비(consume)는 GETDEL/Lua 로 원자 처리해 재사용을 막는다.
 * 키는 사용자당 1개({@code operator:setup:{userSn}})라 재발급 시 이전 토큰이 자동 무효화된다.
 */
public interface OperatorSetupTokenStore {

	/**
	 * 설정토큰 발급 — 새 토큰을 생성해 해시를 저장(TTL)하고 평문 토큰을 1회 반환한다.
	 * 같은 userSn 의 기존 토큰은 덮어써져 무효화된다.
	 *
	 * @return 평문 설정토큰(원문 — 호출측이 발급 응답에만 1회 노출)
	 */
	String issueToken(long userSn);

	/**
	 * 설정토큰 소비(원자) — 토큰이 유효하면 대상 userSn 을 반환하고 토큰을 즉시 폐기(1회성)한다.
	 * 무효(없음/만료/불일치/형식오류)면 null.
	 *
	 * @param token 사용자가 제시한 평문 설정토큰
	 * @return 검증된 대상 userSn, 무효면 null
	 */
	Long consume(String token);
}
