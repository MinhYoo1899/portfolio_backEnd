package egovframework.eeum.account.service.email;

/**
 * 가입 완료(일반 이메일 가입) 시 발행되는 이메일 인증코드 자동 발송 이벤트.
 *
 * <p>리스너가 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 받아 트랜잭션 밖에서 발송한다
 * (가입 커밋 후 — 발송 실패가 가입을 막지 않게). 소셜 가입은 발행하지 않는다(이미 검증 또는 미발송 정책).
 *
 * @param userSn 가입한 사용자일련번호
 */
public record EmailVerificationRequestedEvent(long userSn) {
}
