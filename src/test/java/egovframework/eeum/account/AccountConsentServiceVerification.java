package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.account.impl.AccountServiceImpl;
import egovframework.eeum.account.service.account.impl.PasswordChangeGuard;
import egovframework.eeum.account.vo.ConsentStatusVO;
import egovframework.eeum.account.web.dto.ConsentChangeRequest;
import egovframework.eeum.account.web.dto.ConsentStatusResponse;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.security.PasswordPolicy;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.terms.vo.TrmsVO;

/**
 * 동의 관리(조회/선택약관 변경) 서비스 검증 — mock 기반.
 * 핵심: 선택약관만 변경(필수 철회 거부) / 미시행 약관 거부 / append-only(insertTrmsAgre) / 조회 매핑.
 */
class AccountConsentServiceVerification {

	private static final long USER = 7L;

	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final TermsService termsService = mock(TermsService.class);

	private final AccountServiceImpl service = new AccountServiceImpl(
			accountMapper, mock(PasswordEncoder.class), mock(PasswordPolicy.class), mock(PasswordChangeGuard.class),
			mock(TokenSessionStore.class), mock(AuthRevocationStore.class), mock(FileService.class),
			mock(ApplicationEventPublisher.class), termsService);

	private static TrmsVO terms(long trmsSn, String esntlYn) {
		TrmsVO vo = new TrmsVO();
		vo.setTrmsSn(trmsSn);
		vo.setEsntlYn(esntlYn);
		return vo;
	}

	// ---------------------------------------------------------------- 변경(선택약관)
	@Test
	@DisplayName("선택약관 철회 — 시행 중 선택약관(N)이면 새 이력행 append(agreYn=N)")
	void withdrawOptionalConsent() {
		when(termsService.getEffectiveTerms()).thenReturn(List.of(terms(5L, "N"), terms(1L, "Y")));

		service.changeConsent(USER, new ConsentChangeRequest("5", "N"), "1.2.3.4", "UA");

		verify(accountMapper).insertTrmsAgre(USER, 5L, "N", "WEB", "1.2.3.4", "UA");
	}

	@Test
	@DisplayName("선택약관 재동의 — agreYn=Y 로 새 이력행 append")
	void reAgreeOptionalConsent() {
		when(termsService.getEffectiveTerms()).thenReturn(List.of(terms(5L, "N")));

		service.changeConsent(USER, new ConsentChangeRequest("5", "Y"), "1.2.3.4", "UA");

		verify(accountMapper).insertTrmsAgre(USER, 5L, "Y", "WEB", "1.2.3.4", "UA");
	}

	@Test
	@DisplayName("필수약관 변경 시도 — CONSENT_ESSENTIAL_NOT_CHANGEABLE, 이력 미기록")
	void essentialCannotChange() {
		when(termsService.getEffectiveTerms()).thenReturn(List.of(terms(1L, "Y")));

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeConsent(USER, new ConsentChangeRequest("1", "N"), "1.2.3.4", "UA"));
		assertEquals(AccountErrorCode.CONSENT_ESSENTIAL_NOT_CHANGEABLE, e.getErrorCode());
		verify(accountMapper, never()).insertTrmsAgre(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("미시행/존재X 약관 — CONSENT_TERMS_NOT_FOUND, 이력 미기록")
	void notEffectiveTerms() {
		when(termsService.getEffectiveTerms()).thenReturn(List.of(terms(5L, "N")));

		BusinessException e = assertThrows(BusinessException.class,
				() -> service.changeConsent(USER, new ConsentChangeRequest("999", "N"), "1.2.3.4", "UA"));
		assertEquals(AccountErrorCode.CONSENT_TERMS_NOT_FOUND, e.getErrorCode());
		verify(accountMapper, never()).insertTrmsAgre(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
	}

	// ---------------------------------------------------------------- 조회
	@Test
	@DisplayName("내 동의 현황 — VO를 응답으로 매핑(발생시각 Instant)")
	void getMyConsentsMaps() {
		ConsentStatusVO vo = new ConsentStatusVO();
		vo.setTrmsSn(5L);
		vo.setTrmsTypeCd("MARKETING");
		vo.setVerNo("1.0");
		vo.setTrmsTtl("마케팅 수신 동의");
		vo.setEsntlYn("N");
		vo.setAgreYn("N");
		vo.setAgreDt(LocalDateTime.of(2026, 6, 23, 1, 2, 3));
		when(accountMapper.selectMyConsents(USER)).thenReturn(List.of(vo));

		List<ConsentStatusResponse> result = service.getMyConsents(USER);

		assertEquals(1, result.size());
		assertEquals(5L, result.get(0).trmsSn());
		assertEquals("MARKETING", result.get(0).trmsTypeCd());
		assertEquals("N", result.get(0).agreYn());
		assertEquals("2026-06-23T01:02:03Z", result.get(0).agreDt().toString());
	}
}
