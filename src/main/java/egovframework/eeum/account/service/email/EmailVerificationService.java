package egovframework.eeum.account.service.email;

/**
 * 이메일 소유확인(가입 이메일 인증) 서비스 — 6자리 코드 발급/검증.
 *
 * <p>완화형 정책: 가입 즉시 ACTIVE 유지 + 미검증 로그인 허용. 본 서비스는 검증 상태만 갱신한다.
 * 검증 성공 시 USER_ACNT.EML_VRFD_YN='Y' + 토큰 세대 무효화(stale JWT 정리).
 */
public interface EmailVerificationService {

	/** 인증코드 발송 요청 — 이미 검증이면 409, 아니면 코드 발급 후 발송 포트로 전송. */
	void requestVerification(long userSn);

	/** 인증코드 검증 — OK 면 검증 완료 처리, 그 외는 사유별 오류코드. */
	void verify(long userSn, String code);
}
