package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.email.EmailVerificationChallengeStore;
import egovframework.eeum.account.service.email.EmailVerificationChallengeStore.VerifyResult;
import egovframework.eeum.account.service.email.EmailVerificationMailCommand;
import egovframework.eeum.account.service.email.EmailVerificationMailPort;
import egovframework.eeum.account.service.email.impl.EmailVerificationServiceImpl;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.base.exception.BusinessException;

/**
 * 이메일 소유확인 서비스 검증(DB/Redis 없이 mock) — 이미검증 409, 성공 시 갱신(세대 무효화 없음), 사유별 오류 매핑.
 */
class EmailVerificationServiceVerification {

	private static final String PURPOSE = EmailVerificationChallengeStore.PURPOSE_SIGNUP_EMAIL_VERIFY;
	private static final long USER_SN = 7L;
	private static final String EMAIL = "user@eeum.com";

	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final EmailVerificationChallengeStore challengeStore = mock(EmailVerificationChallengeStore.class);
	private final EmailVerificationMailPort mailPort = mock(EmailVerificationMailPort.class);

	private final EmailVerificationServiceImpl service = new EmailVerificationServiceImpl(
			accountMapper, challengeStore, mailPort);

	private static AccountVO account(String emlVrfdYn) {
		return account(emlVrfdYn, "ACTIVE");
	}

	private static AccountVO account(String emlVrfdYn, String acntStts) {
		AccountVO vo = new AccountVO();
		vo.setUserSn(USER_SN);
		vo.setEmlAddr(EMAIL);
		vo.setEmlVrfdYn(emlVrfdYn);
		vo.setAcntStts(acntStts);
		return vo;
	}

	// ------------------------------------------------------------ requestVerification
	@Test
	@DisplayName("발송 요청 — 미검증이면 코드 발급 후 발송 포트로 전송")
	void requestSendsCode() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.issueCode(USER_SN, EMAIL, PURPOSE)).thenReturn("012345");

		service.requestVerification(USER_SN);

		verify(challengeStore).issueCode(USER_SN, EMAIL, PURPOSE);
		// ttlSeconds 는 @Value 주입(스프링 밖 수동생성이라 0) — 코드/이메일 전달만 확인.
		EmailVerificationMailCommand expected = new EmailVerificationMailCommand(EMAIL, "012345", 0L);
		verify(mailPort).sendVerificationCode(eq(expected));
	}

	@Test
	@DisplayName("발송 요청 — 이미 검증이면 EMAIL_ALREADY_VERIFIED(409), 발급/발송 안 함")
	void requestAlreadyVerified() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("Y"));

		BusinessException e = assertThrows(BusinessException.class, () -> service.requestVerification(USER_SN));
		assertEquals(AccountErrorCode.EMAIL_ALREADY_VERIFIED, e.getErrorCode());
		verify(challengeStore, never()).issueCode(anyLong(), anyString(), anyString());
		verify(mailPort, never()).sendVerificationCode(any());
	}

	@Test
	@DisplayName("발송 요청 — 계정 없으면 ACCOUNT_NOT_FOUND")
	void requestAccountNotFound() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.requestVerification(USER_SN));
		assertEquals(AccountErrorCode.ACCOUNT_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("발송 요청 — 정지/반려 계정이면 ACCOUNT_NOT_ACTIVE(403), 발급/발송 안 함")
	void requestNotActive() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N", "SUSPENDED"));

		BusinessException e = assertThrows(BusinessException.class, () -> service.requestVerification(USER_SN));
		assertEquals(AccountErrorCode.ACCOUNT_NOT_ACTIVE, e.getErrorCode());
		verify(challengeStore, never()).issueCode(anyLong(), anyString(), anyString());
		verify(mailPort, never()).sendVerificationCode(any());
	}

	@Test
	@DisplayName("발송 요청 — PENDING(파트너 승인대기) 계정은 허용")
	void requestPendingAllowed() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N", "PENDING"));
		when(challengeStore.issueCode(USER_SN, EMAIL, PURPOSE)).thenReturn("012345");

		service.requestVerification(USER_SN);

		verify(challengeStore).issueCode(USER_SN, EMAIL, PURPOSE);
	}

	// ------------------------------------------------------------ verify
	@Test
	@DisplayName("검증 — 정지/반려 계정이면 ACCOUNT_NOT_ACTIVE(403), store.verify 호출 안 함")
	void verifyNotActive() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N", "REJECTED"));

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.ACCOUNT_NOT_ACTIVE, e.getErrorCode());
		verify(challengeStore, never()).verify(anyLong(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("검증 OK — updateEmailVerified(EMAIL_CODE) 호출(세대 무효화 안 함)")
	void verifyOk() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.OK);
		when(accountMapper.updateEmailVerified(USER_SN, "EMAIL_CODE")).thenReturn(1);

		service.verify(USER_SN, "012345");

		verify(accountMapper).updateEmailVerified(USER_SN, "EMAIL_CODE");
	}

	@Test
	@DisplayName("검증 OK — PENDING(파트너 승인대기) 계정도 갱신 호출")
	void verifyOkPending() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N", "PENDING"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.OK);
		when(accountMapper.updateEmailVerified(USER_SN, "EMAIL_CODE")).thenReturn(1);

		service.verify(USER_SN, "012345");

		verify(accountMapper).updateEmailVerified(USER_SN, "EMAIL_CODE");
	}

	@Test
	@DisplayName("검증 OK 인데 updated==0 + 재조회도 여전히 활성/미검증 → EMAIL_ALREADY_VERIFIED")
	void verifyOkButAlreadyUpdated() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.OK);
		when(accountMapper.updateEmailVerified(USER_SN, "EMAIL_CODE")).thenReturn(0);

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.EMAIL_ALREADY_VERIFIED, e.getErrorCode());
	}

	@Test
	@DisplayName("검증 OK 인데 updated==0 + 재조회 시 정지로 바뀜(경합) → ACCOUNT_NOT_ACTIVE")
	void verifyOkButStatusChangedToSuspended() {
		// 진입 시 ACTIVE → 코드 검증 통과 → UPDATE 직전 SUSPENDED 로 변경되어 0건 → 재조회로 NOT_ACTIVE 분기.
		when(accountMapper.selectAccountByUserSn(USER_SN))
				.thenReturn(account("N", "ACTIVE"), account("N", "SUSPENDED"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.OK);
		when(accountMapper.updateEmailVerified(USER_SN, "EMAIL_CODE")).thenReturn(0);

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.ACCOUNT_NOT_ACTIVE, e.getErrorCode());
	}

	@Test
	@DisplayName("검증 INVALID → EMAIL_VERIFICATION_INVALID, 갱신 안 함")
	void verifyInvalid() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "000000")).thenReturn(VerifyResult.INVALID);

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "000000"));
		assertEquals(AccountErrorCode.EMAIL_VERIFICATION_INVALID, e.getErrorCode());
		verify(accountMapper, never()).updateEmailVerified(anyLong(), anyString());
	}

	@Test
	@DisplayName("검증 EXPIRED → EMAIL_VERIFICATION_EXPIRED")
	void verifyExpired() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.EXPIRED);

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.EMAIL_VERIFICATION_EXPIRED, e.getErrorCode());
		verify(accountMapper, never()).updateEmailVerified(anyLong(), anyString());
	}

	@Test
	@DisplayName("검증 TOO_MANY → EMAIL_VERIFICATION_TOO_MANY")
	void verifyTooMany() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("N"));
		when(challengeStore.verify(USER_SN, EMAIL, PURPOSE, "012345")).thenReturn(VerifyResult.TOO_MANY);

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.EMAIL_VERIFICATION_TOO_MANY, e.getErrorCode());
	}

	@Test
	@DisplayName("검증 — 이미 검증된 계정이면 EMAIL_ALREADY_VERIFIED, store.verify 호출 안 함")
	void verifyAlreadyVerified() {
		when(accountMapper.selectAccountByUserSn(USER_SN)).thenReturn(account("Y"));

		BusinessException e = assertThrows(BusinessException.class, () -> service.verify(USER_SN, "012345"));
		assertEquals(AccountErrorCode.EMAIL_ALREADY_VERIFIED, e.getErrorCode());
		verify(challengeStore, never()).verify(anyLong(), anyString(), anyString(), anyString());
	}
}
