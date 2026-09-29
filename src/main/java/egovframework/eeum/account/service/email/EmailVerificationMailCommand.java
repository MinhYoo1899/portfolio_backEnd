package egovframework.eeum.account.service.email;

/**
 * 이메일 인증코드 발송 명령 — 발송 포트({@link EmailVerificationMailPort}) 입력.
 *
 * @param toEmail    수신 이메일(현재 계정의 EML_ADDR — 챌린지 email 과 일치 검증을 통과한 값)
 * @param code       6자리 인증코드(평문 — 발송 시점 외 보관 금지)
 * @param ttlSeconds 코드 유효시간(초) — 본문 안내용
 */
public record EmailVerificationMailCommand(
		String toEmail,
		String code,
		long ttlSeconds) {
}
