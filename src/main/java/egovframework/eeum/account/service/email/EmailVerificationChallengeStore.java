package egovframework.eeum.account.service.email;

/**
 * 이메일 인증 챌린지 저장소 — 6자리 코드의 발급/검증을 1회성·TTL·시도제한으로 관리한다(Redis).
 *
 * <p>보안(codex MUST): 평문 코드는 어디에도 저장하지 않는다. Redis 에는
 * {@code HMAC-SHA256(secret, purpose|userSn|email|code)} 의 hex 만 둔다.
 * 발급 시 반환한 평문 코드는 호출측이 즉시 발송 포트로 넘기고 메모리 밖으로 내보내지 않는다.
 *
 * <p>purpose 도메인 분리: HMAC 입력에 purpose 를 포함해 동일 secret 재사용 위험을 완화한다.
 * 이번 범위는 {@link #PURPOSE_SIGNUP_EMAIL_VERIFY} 뿐이며, 향후 이메일 변경·비번재설정 등으로 확장한다.
 */
public interface EmailVerificationChallengeStore {

	/** 가입 이메일 소유확인 — 이번 범위. */
	String PURPOSE_SIGNUP_EMAIL_VERIFY = "SIGNUP_EMAIL_VERIFY";

	/** 코드 검증 결과. */
	enum VerifyResult {
		/** 일치 — 챌린지 소비됨(삭제). */
		OK,
		/** 불일치(또는 챌린지 없음). */
		INVALID,
		/** 만료(챌린지 없음 또는 TTL 경과). */
		EXPIRED,
		/** 검증 시도 초과 — 챌린지 폐기됨. */
		TOO_MANY
	}

	/**
	 * 인증코드 발급 — 쿨다운/일일한도 검증 후 6자리 코드 생성, 챌린지를 TTL 로 저장하고 평문 코드를 반환한다.
	 * 쿨다운/일일한도 초과 시 {@code BusinessException}(429).
	 *
	 * @return 발송할 평문 코드(보관 금지 — 즉시 발송)
	 */
	String issueCode(long userSn, String email, String purpose);

	/**
	 * 인증코드 검증 — HMAC 비교 + 시도 카운트. 성공 시 챌린지 소비(삭제), 시도 초과 시 폐기.
	 * (email 은 호출측이 현재 계정 EML_ADDR 로 넘긴다 — 챌린지의 email 과 다르면 INVALID.)
	 */
	VerifyResult verify(long userSn, String email, String purpose, String code);
}
