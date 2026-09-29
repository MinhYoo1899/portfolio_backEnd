package egovframework.eeum.auth.service.social;

/**
 * OAuth 콜백 처리 결과를 1회용 result 토큰으로 보관하는 저장소(프론트가 토큰을 노출 없이 교환).
 *
 * <p>키 {@code oauth:result:{resultToken}}, 짧은 TTL, consume 은 GETDEL(원자 1회 소비).
 * <b>access/refresh 토큰은 절대 저장하지 않는다</b> — 결과 종류(LOGIN/SIGNUP)와 식별 정보만 보관하고,
 * 실제 토큰 발급은 result 교환 단계에서 수행한다(전략 — Redis 에 토큰 영속 금지).
 */
public interface OAuthResultStore {

	/** 콜백 결과를 보관하고 1회용 난수 result 토큰을 발급. */
	String issue(OAuthResultData data);

	/** result 토큰으로 결과를 1회 소비(consume) — 없거나 만료/재사용/형식오류면 null. */
	OAuthResultData consume(String resultToken);

	/**
	 * result 저장값 — 토큰 미포함. type=LOGIN 이면 (userSn, clientType), type=SIGNUP 이면 (signupToken, email, name).
	 *
	 * @param type        LOGIN | SIGNUP
	 * @param userSn      type=LOGIN — 발급 대상 사용자 일련번호
	 * @param clientType  type=LOGIN — WEB/APP(refresh 전달 분기)
	 * @param signupToken type=SIGNUP — 가입 완료 호출용 1회용 토큰
	 * @param email       type=SIGNUP — provider 이메일(표시/프리필)
	 * @param name        type=SIGNUP — provider 이름(표시/프리필)
	 * @param bindHash    흐름 바인딩 쿠키값의 해시 — result 교환이 흐름을 시작한 브라우저인지 대조(로그인 CSRF 차단)
	 */
	record OAuthResultData(
			String type,
			Long userSn,
			String clientType,
			String signupToken,
			String email,
			String name,
			String bindHash) {

		public static final String TYPE_LOGIN = "LOGIN";
		public static final String TYPE_SIGNUP = "SIGNUP";

		public static OAuthResultData login(long userSn, String clientType, String bindHash) {
			return new OAuthResultData(TYPE_LOGIN, userSn, clientType, null, null, null, bindHash);
		}

		public static OAuthResultData signup(String signupToken, String email, String name, String bindHash) {
			return new OAuthResultData(TYPE_SIGNUP, null, null, signupToken, email, name, bindHash);
		}
	}
}
