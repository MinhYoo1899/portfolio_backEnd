package egovframework.eeum.account.service.email;

/**
 * 이메일 인증코드 발송 포트 — 발송 채널 추상화.
 *
 * <p>구현 교체(dev 더미 ↔ 운영 SMTP/SES)를 위해 인터페이스로 분리한다. 현재는 dev 더미
 * ({@code impl.DevLoggingMailAdapter})만 제공하고 실제 발송 어댑터는 미구현(후속).
 * 호출은 트랜잭션 밖(AFTER_COMMIT)에서만 이뤄진다(가입 흐름 비차단).
 */
public interface EmailVerificationMailPort {

	/** 인증코드 발송. 실패는 구현/호출측 정책에 따라 처리(가입은 swallow). */
	void sendVerificationCode(EmailVerificationMailCommand cmd);
}
