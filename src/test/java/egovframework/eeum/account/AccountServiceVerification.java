package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.account.impl.AccountServiceImpl;
import egovframework.eeum.account.service.account.impl.PasswordChangeGuard;
import egovframework.eeum.account.web.dto.AccountDeleteRequest;
import egovframework.eeum.account.web.dto.PasswordChangeRequest;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.security.PasswordPolicy;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.terms.service.terms.TermsService;

/**
 * 계정 서비스 검증(DB 없이 mock) — 탈퇴 시 프로필 이미지 정리(공개 다운로드 누수 차단, codex High 1)와
 * 비밀번호 변경(소셜 분기·현재비번·동일거부·세션 무효화).
 */
class AccountServiceVerification {

	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
	private final PasswordChangeGuard passwordChangeGuard = mock(PasswordChangeGuard.class);
	private final TokenSessionStore tokenSessionStore = mock(TokenSessionStore.class);
	private final AuthRevocationStore authRevocationStore = mock(AuthRevocationStore.class);
	private final FileService fileService = mock(FileService.class);
	private final TermsService termsService = mock(TermsService.class);

	private final org.springframework.context.ApplicationEventPublisher eventPublisher =
			mock(org.springframework.context.ApplicationEventPublisher.class);

	private final AccountServiceImpl service = new AccountServiceImpl(
			accountMapper, passwordEncoder, passwordPolicy, passwordChangeGuard,
			tokenSessionStore, authRevocationStore, fileService, eventPublisher, termsService);

	private static AccountDeleteRequest req() {
		return new AccountDeleteRequest("pw1234!");
	}

	private static PasswordChangeRequest pwReq() {
		return new PasswordChangeRequest("oldPw1!", "newPw2@");
	}

	// ------------------------------------------------------------------ 탈퇴
	@Test
	@DisplayName("탈퇴 — 프로필 이미지가 있으면 FileService 로 soft delete 하고 PRFL_IMG_FILE_SN 을 NULL 로")
	void withdrawCleansUpProfileImage() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("pw1234!", "ENC")).thenReturn(true);
		when(accountMapper.withdrawAccount(7L)).thenReturn(1);
		when(accountMapper.selectPrflImgFileSnForUpdate(7L)).thenReturn(42L);

		service.delete(7L, req());

		verify(fileService).softDeleteByAtchFileSn(42L, 7L); // 공개스코프용(PROFILE_IMAGE 처리)
		verify(accountMapper).clearPrflImgFileSn(7L);
		verify(tokenSessionStore).revokeAll(7L);
		verify(authRevocationStore).bumpGeneration(7L);
	}

	@Test
	@DisplayName("탈퇴 — 프로필 이미지가 없으면(null) 파일 정리/NULL update 호출 안 함")
	void withdrawWithoutProfileImage() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("pw1234!", "ENC")).thenReturn(true);
		when(accountMapper.withdrawAccount(7L)).thenReturn(1);
		when(accountMapper.selectPrflImgFileSnForUpdate(7L)).thenReturn(null);

		service.delete(7L, req());

		verify(fileService, never()).softDeleteByAtchFileSn(anyLong(), anyLong());
		verify(accountMapper, never()).clearPrflImgFileSn(anyLong());
		verify(tokenSessionStore).revokeAll(7L);
	}

	@Test
	@DisplayName("탈퇴 — 비밀번호 불일치면 ACCOUNT_PASSWORD_MISMATCH(파일 정리 진입 안 함)")
	void withdrawPasswordMismatch() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("pw1234!", "ENC")).thenReturn(false);

		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(7L, req()));
		assertEquals(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH, e.getErrorCode());
		verify(accountMapper, never()).withdrawAccount(anyLong());
		verify(fileService, never()).softDeleteByAtchFileSn(anyLong(), anyLong());
	}

	// ------------------------------------------------------------ 비밀번호 변경
	@Test
	@DisplayName("비밀번호 변경 — 성공 시 재해시 저장 + 전체 세션 무효화(refresh+워터마크)")
	void changePasswordSuccess() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("oldPw1!", "ENC")).thenReturn(true);   // 현재 비번 일치
		when(passwordEncoder.matches("newPw2@", "ENC")).thenReturn(false);  // 새 비번 ≠ 기존
		when(passwordEncoder.encode("newPw2@")).thenReturn("ENC2");
		when(accountMapper.updateEnpswd(7L, "ENC2")).thenReturn(1);

		service.changePassword(7L, pwReq());

		verify(passwordChangeGuard).registerAttemptAndCheck(7L); // 시도 등록(원자적 rate limit)
		verify(passwordPolicy).validate("newPw2@");
		verify(accountMapper).updateEnpswd(7L, "ENC2");
		verify(passwordChangeGuard).reset(7L);
		verify(tokenSessionStore).revokeAll(7L);
		verify(authRevocationStore).bumpGeneration(7L);
	}

	@Test
	@DisplayName("비밀번호 변경 — 소셜 전용(ENPSWD null)이면 ACCOUNT_PASSWORD_NOT_SET, 갱신/무효화 안 함")
	void changePasswordSocialOnly() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.changePassword(7L, pwReq()));
		assertEquals(AccountErrorCode.ACCOUNT_PASSWORD_NOT_SET, e.getErrorCode());
		verify(accountMapper, never()).updateEnpswd(anyLong(), anyString());
		verify(tokenSessionStore, never()).revokeAll(anyLong());
	}

	@Test
	@DisplayName("비밀번호 변경 — 현재 비번 불일치면 ACCOUNT_PASSWORD_MISMATCH")
	void changePasswordCurrentMismatch() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("oldPw1!", "ENC")).thenReturn(false);

		BusinessException e = assertThrows(BusinessException.class, () -> service.changePassword(7L, pwReq()));
		assertEquals(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH, e.getErrorCode());
		verify(accountMapper, never()).updateEnpswd(anyLong(), anyString());
		verify(passwordChangeGuard).registerAttemptAndCheck(7L); // 시도 집계됨(성공 reset 없음 → 누적)
		verify(passwordChangeGuard, never()).reset(anyLong());
	}

	@Test
	@DisplayName("비밀번호 변경 — 새 비번이 기존과 동일하면 ACCOUNT_PASSWORD_SAME_AS_OLD")
	void changePasswordSameAsOld() {
		when(accountMapper.selectEnpswdByUserSn(7L)).thenReturn("ENC");
		when(passwordEncoder.matches("oldPw1!", "ENC")).thenReturn(true);
		when(passwordEncoder.matches("newPw2@", "ENC")).thenReturn(true); // 새 비번이 기존과 동일

		BusinessException e = assertThrows(BusinessException.class, () -> service.changePassword(7L, pwReq()));
		assertEquals(AccountErrorCode.ACCOUNT_PASSWORD_SAME_AS_OLD, e.getErrorCode());
		verify(accountMapper, never()).updateEnpswd(anyLong(), anyString());
		verify(tokenSessionStore, never()).revokeAll(anyLong());
	}
}
