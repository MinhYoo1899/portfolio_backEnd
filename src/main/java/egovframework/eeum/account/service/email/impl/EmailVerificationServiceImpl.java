package egovframework.eeum.account.service.email.impl;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.email.EmailVerificationChallengeStore;
import egovframework.eeum.account.service.email.EmailVerificationChallengeStore.VerifyResult;
import egovframework.eeum.account.service.email.EmailVerificationMailCommand;
import egovframework.eeum.account.service.email.EmailVerificationMailPort;
import egovframework.eeum.account.service.email.EmailVerificationService;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import lombok.RequiredArgsConstructor;

/**
 * 이메일 소유확인 서비스 구현.
 *
 * <p>흐름: 계정 조회(EML_ADDR/EML_VRFD_YN) → 챌린지 발급/검증 → 검증 성공 시 USER_ACNT 갱신.
 * 챌린지의 email 과 현재 계정 EML_ADDR 일치를 보장(코드를 계정에 종속).
 *
 * <p>세대 무효화(bumpGeneration) 안 함(codex) — 이메일 검증은 보안 강화(N→Y)라 기존 세션을 죽일 이유가 없고,
 * 토큰에 emailVerified 가 없어 무효화해도 실익 없이 재로그인만 강제된다. 검증 상태는 detail/me 조회로 반영한다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

	/** 코드 검증 성공 시 기록하는 출처. */
	private static final String SRC_EMAIL_CODE = "EMAIL_CODE";

	/** 이메일 인증 허용 계정 상태 — ACTIVE(일반/승인완료) + PENDING(파트너 승인대기). 정지·반려는 차단(탈퇴는 DEL_YN 으로 조회에서 제외). */
	private static final Set<String> VERIFIABLE_STATUSES = Set.of("ACTIVE", "PENDING");

	private final AccountMapper accountMapper;
	private final EmailVerificationChallengeStore challengeStore;
	private final EmailVerificationMailPort mailPort;

	/** 코드 유효시간(초) — 발송 본문 안내용(저장소 TTL 과 동일 값). */
	@Value("${eeum.email.verification.ttl-seconds:300}")
	private long ttlSeconds;

	@Override
	public void requestVerification(long userSn) {
		AccountVO account = loadActiveAccount(userSn);
		if ("Y".equals(account.getEmlVrfdYn())) {
			throw new BusinessException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
		}
		String email = account.getEmlAddr();
		String code = challengeStore.issueCode(userSn, email,
				EmailVerificationChallengeStore.PURPOSE_SIGNUP_EMAIL_VERIFY);
		mailPort.sendVerificationCode(new EmailVerificationMailCommand(email, code, ttlSeconds));
	}

	@Override
	@Transactional
	@Audit(actn = "EMAIL_VERIFY", trgtType = "USER_ACNT", trgtId = "#userSn")
	public void verify(long userSn, String code) {
		AccountVO account = loadActiveAccount(userSn);
		if ("Y".equals(account.getEmlVrfdYn())) {
			throw new BusinessException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
		}
		VerifyResult result = challengeStore.verify(userSn, account.getEmlAddr(),
				EmailVerificationChallengeStore.PURPOSE_SIGNUP_EMAIL_VERIFY, code);
		switch (result) {
			case OK -> {
				int updated = accountMapper.updateEmailVerified(userSn, SRC_EMAIL_CODE);
				if (updated == 0) {
					// 갱신 0건 — 코드 검증은 통과했으나 UPDATE 조건(미검증 + 활성/대기)에 안 맞는 경합 상황.
					// 재조회로 원인을 구분해 정확한 오류로 매핑(검증 직전 정지/반려로 바뀐 경우를 '이미검증'으로 오인 방지, codex SHOULD).
					AccountVO current = accountMapper.selectAccountByUserSn(userSn);
					if (current == null) {
						throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
					}
					if (!VERIFIABLE_STATUSES.contains(current.getAcntStts())) {
						throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
					}
					throw new BusinessException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
				}
			}
			case EXPIRED -> throw new BusinessException(AccountErrorCode.EMAIL_VERIFICATION_EXPIRED);
			case TOO_MANY -> throw new BusinessException(AccountErrorCode.EMAIL_VERIFICATION_TOO_MANY);
			case INVALID -> throw new BusinessException(AccountErrorCode.EMAIL_VERIFICATION_INVALID);
		}
	}

	private AccountVO loadActiveAccount(long userSn) {
		AccountVO account = accountMapper.selectAccountByUserSn(userSn);
		if (account == null) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
		}
		// 정지/반려 계정은 access token 잔여 수명 동안에도 이메일 인증 발급·검증을 막는다(codex MUST).
		if (!VERIFIABLE_STATUSES.contains(account.getAcntStts())) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		}
		return account;
	}
}
