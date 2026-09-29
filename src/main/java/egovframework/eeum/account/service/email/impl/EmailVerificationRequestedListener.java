package egovframework.eeum.account.service.email.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import egovframework.eeum.account.service.email.EmailVerificationRequestedEvent;
import egovframework.eeum.account.service.email.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 가입 후 인증코드 자동 발송 리스너 — 가입 트랜잭션 커밋 후(AFTER_COMMIT) 동기로 발송한다.
 *
 * <p>@Async 미사용(dev 더미라 동기로 충분). 발송 실패는 try-catch 로 swallow 해 가입 완료를 막지 않는다
 * (이미 가입은 커밋됨 — 사용자는 재발송 API 로 다시 받을 수 있다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationRequestedListener {

	private final EmailVerificationService emailVerificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
		try {
			emailVerificationService.requestVerification(event.userSn());
		} catch (RuntimeException e) {
			// 가입은 이미 커밋됨 — 발송 실패가 가입을 무효화하지 않게 swallow(재발송 API 로 복구 가능).
			log.warn("[EMAIL-VERIFY] 가입 후 인증코드 자동 발송 실패(가입은 정상). userSn={}", event.userSn(), e);
		}
	}
}
