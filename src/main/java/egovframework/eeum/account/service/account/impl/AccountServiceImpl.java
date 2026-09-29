package egovframework.eeum.account.service.account.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.web.dto.AccountDeleteRequest;
import egovframework.eeum.account.web.dto.AccountDetailResponse;
import egovframework.eeum.account.web.dto.AccountInsertRequest;
import egovframework.eeum.account.web.dto.AccountUpdateRequest;
import egovframework.eeum.account.web.dto.PasswordChangeRequest;
import egovframework.eeum.account.web.dto.TrmsAgreItem;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.terms.vo.TrmsVO;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.AccountService;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.email.EmailVerificationRequestedEvent;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.security.PasswordPolicy;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 계정 서비스 구현 — 일반회원.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	private static final String USER_TYPE_MEMBER = "MEMBER";
	private static final String LGN_TYPE_EMAIL = "EMAIL";
	private static final String LGN_TYPE_SOCIAL = "SOCIAL";
	/** 가입 직후 상태 — 전화 본인인증(포트원) 연동 전 임시로 ACTIVE */
	private static final String SIGNUP_STTS = "ACTIVE";
	/** 동의 변경 채널 — 본인 웹/앱에서의 동의관리. */
	private static final String AGRE_CHNL_WEB = "WEB";

	private final AccountMapper accountMapper;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final PasswordChangeGuard passwordChangeGuard;
	private final TokenSessionStore tokenSessionStore;
	private final AuthRevocationStore authRevocationStore;
	private final FileService fileService;
	private final ApplicationEventPublisher eventPublisher;
	private final TermsService termsService;

	/** 가입 유형→기본 역할코드 매핑(데이터 주도 RBAC — 값은 ROLE 테이블 데이터) */
	@Value("${eeum.account.default-role.member}")
	private String memberDefaultRoleCd;

	@Override
	@Transactional
	@Audit(actn = "ACCOUNT_INSERT", trgtType = "USER_ACNT", trgtId = "#result.userSn()")
	public AccountDetailResponse insert(AccountInsertRequest request, String cntnIp, String userAgnt) {
		if (accountMapper.existsActiveEmlAddr(request.emlAddr()) > 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE);
		}
		passwordPolicy.validate(request.pswd()); // 가입도 변경과 동일 정책(2종·72byte) 강제 — 정책 단일화(codex MUST ④)
		validateEssentialTerms(termsService.getEffectiveTerms(), request.trmsAgreList());

		AccountVO account = new AccountVO();
		account.setEmlAddr(request.emlAddr());
		account.setEnpswd(passwordEncoder.encode(request.pswd()));
		account.setUserTypeCd(USER_TYPE_MEMBER);
		account.setLgnType(LGN_TYPE_EMAIL);
		account.setAcntStts(SIGNUP_STTS);
		account.setUserNm(request.userNm());
		account.setNcnm(request.ncnm());
		try {
			accountMapper.insertAccount(account);
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 동시 가입 경합 — DB 활성유니크(UK_USER_ACNT_EML)가 최종 방어 (codex 필수수정 2)
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE,
					AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE.defaultMessage(), e);
		}
		long userSn = account.getUserSn();

		// 불변식: 일반/소셜 가입은 같은 트랜잭션에서 USER_DTL_INFO 1:1 행을 반드시 생성한다(프로필 교체가 의존).
		accountMapper.insertUserDtlInfo(userSn);
		recordTermsAgreements(accountMapper, userSn, request.trmsAgreList(), request.agreChnlOrDefault(), cntnIp, userAgnt);
		assignRole(accountMapper, userSn, memberDefaultRoleCd);

		// 가입 이메일 소유확인 — 커밋 후(AFTER_COMMIT) 인증코드 자동 발송(트랜잭션 밖, 실패는 swallow). 일반가입만.
		eventPublisher.publishEvent(new EmailVerificationRequestedEvent(userSn));

		return detail(userSn);
	}

	@Override
	@Transactional(readOnly = true)
	public AccountDetailResponse detail(long userSn) {
		AccountVO account = accountMapper.selectAccountByUserSn(userSn);
		if (account == null) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
		}
		return AccountDetailResponse.from(account);
	}

	@Override
	@Transactional(readOnly = true)
	public List<egovframework.eeum.account.web.dto.ConsentStatusResponse> getMyConsents(long userSn) {
		return accountMapper.selectMyConsents(userSn).stream()
				.map(egovframework.eeum.account.web.dto.ConsentStatusResponse::from)
				.toList();
	}

	@Override
	@Transactional
	@Audit(actn = "CONSENT_CHANGE", trgtType = "TRMS_AGRE", trgtId = "#request.trmsSn()")
	public void changeConsent(long userSn, egovframework.eeum.account.web.dto.ConsentChangeRequest request,
			String cntnIp, String userAgnt) {
		long trmsSn = request.trmsSnAsLong();
		// 시행 중 약관만 대상(미시행/존재X 거부). 선택약관(ESNTL_YN='N')만 변경 가능 — 필수약관 철회 금지.
		TrmsVO target = termsService.getEffectiveTerms().stream()
				.filter(t -> t.getTrmsSn() != null && t.getTrmsSn() == trmsSn)
				.findFirst()
				.orElseThrow(() -> new BusinessException(AccountErrorCode.CONSENT_TERMS_NOT_FOUND));
		if ("Y".equals(target.getEsntlYn())) {
			throw new BusinessException(AccountErrorCode.CONSENT_ESSENTIAL_NOT_CHANGEABLE);
		}
		// append-only — 동의/철회 모두 새 이력 행 추가(기존 행 UPDATE 금지, 법적 증적 보존). 채널 WEB.
		accountMapper.insertTrmsAgre(userSn, trmsSn, request.agreYn(), AGRE_CHNL_WEB, cntnIp, userAgnt);
	}

	@Override
	@Transactional
	@Audit(actn = "ACCOUNT_UPDATE", trgtType = "USER_ACNT", trgtId = "#userSn")
	public void update(long userSn, AccountUpdateRequest request) {
		int updated = accountMapper.updateAccount(userSn, request.userNm(), request.ncnm());
		if (updated == 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
		}
	}

	@Override
	@Transactional
	@Audit(actn = "ACCOUNT_DELETE", trgtType = "USER_ACNT", trgtId = "#userSn")
	public void delete(long userSn, AccountDeleteRequest request) {
		String enpswd = accountMapper.selectEnpswdByUserSn(userSn);
		if (enpswd == null || !passwordEncoder.matches(request.pswd(), enpswd)) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH);
		}
		int updated = accountMapper.withdrawAccount(userSn);
		if (updated == 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
		}
		// 소셜연결도 함께 해제 — 같은 소셜계정으로 재가입 가능하게(활성 유니크 해소, codex/Claude 권고)
		accountMapper.softDeleteSocialByUserSn(userSn);
		// 프로필 이미지 정리 — 공개 다운로드 allowlist 에 PROFILE_IMAGE 가 포함돼 탈퇴 후에도 URL 로 받을 수 있는 누수 차단(codex High 1).
		// account→file 은 FileService 경유(ArchUnit 준수). softDeleteByAtchFileSn 은 공개스코프용이라 PROFILE_IMAGE 처리됨.
		// 행 잠금(FOR UPDATE) — 동시 프로필 교체(ProfileImageSwapper)와 직렬화하여 새 파일이 정리 누락되는 경합 방지(codex 재검증 High).
		Long prflImgFileSn = accountMapper.selectPrflImgFileSnForUpdate(userSn);
		if (prflImgFileSn != null) {
			fileService.softDeleteByAtchFileSn(prflImgFileSn, userSn);
			accountMapper.clearPrflImgFileSn(userSn);
		}
		// 탈퇴 즉시 전체 세션 무효화 — 세대 증가(access/refresh 옛 세대 일괄 거부) + refresh 세션 폐기.
		// (트랜잭션 내 호출: Redis 실패 시 DB 롤백돼 보안상 보수적. codex SHOULD 인정)
		authRevocationStore.bumpGeneration(userSn);
		tokenSessionStore.revokeAll(userSn);
	}

	@Override
	@Transactional
	@Audit(actn = "ACCOUNT_PASSWORD_CHANGE", trgtType = "USER_ACNT", trgtId = "#userSn")
	public void changePassword(long userSn, PasswordChangeRequest request) {
		// 현재 비밀번호 온라인 추측 차단 — 시도 등록+임계 판정을 원자 처리, 초과 시 429(codex SHOULD ④/⑧).
		passwordChangeGuard.registerAttemptAndCheck(userSn);
		String enpswd = accountMapper.selectEnpswdByUserSn(userSn);
		// 소셜 전용(비밀번호 미설정) — 변경 대상 아님(409). reset 처럼 generic 할 필요 없음(인증된 본인 대상).
		if (enpswd == null) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_NOT_SET);
		}
		// 현재 비밀번호 재확인(MUST) — 탈취된 access 로 비밀번호가 바뀌는 것 차단(실패는 위 카운터에 이미 집계됨).
		if (!passwordEncoder.matches(request.currentPassword(), enpswd)) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH);
		}
		// 현재 비밀번호 추측 성공 — 카운터 리셋. 이후 새 비밀번호 정책/동일거부 실패는 brute-force 가 아니라 누적시키지 않는다.
		passwordChangeGuard.reset(userSn);
		passwordPolicy.validate(request.newPassword());
		// 기존과 동일한 비밀번호로의 변경 거부.
		if (passwordEncoder.matches(request.newPassword(), enpswd)) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_SAME_AS_OLD);
		}
		int updated = accountMapper.updateEnpswd(userSn, passwordEncoder.encode(request.newPassword()));
		if (updated == 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
		}
		// 변경 즉시 전체 세션 무효화 — 세대 증가로 지금까지 발급된 access/refresh 전부 거부(재로그인 필요) + refresh 세션 폐기.
		// (트랜잭션 내 호출: Redis 실패 시 DB 롤백돼 비밀번호도 안 바뀜 — 보안상 보수적, codex SHOULD 인정)
		authRevocationStore.bumpGeneration(userSn);
		tokenSessionStore.revokeAll(userSn);
	}

	@Override
	@Transactional(readOnly = true)
	public Long findUserSnBySocial(String prvdrCd, String prvdrUserId) {
		return accountMapper.selectUserSnBySocial(prvdrCd, prvdrUserId);
	}

	@Override
	@Transactional
	@Audit(actn = "ACCOUNT_SOCIAL_INSERT", trgtType = "USER_ACNT", trgtId = "#result")
	public long createSocialAccount(String prvdrCd, String prvdrUserId, String emlAddr, String userNm,
			boolean emailVerified, List<TrmsAgreItem> trmsAgreList, String agreChnlCd, String cntnIp, String userAgnt) {
		validateEssentialTerms(termsService.getEffectiveTerms(), trmsAgreList);
		// 동일 이메일 자동연결 금지(전략 §2) — 기존 계정 있으면 거부(명시 연동은 2단계)
		if (emlAddr != null && accountMapper.existsActiveEmlAddr(emlAddr) > 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE);
		}

		AccountVO account = new AccountVO();
		account.setEmlAddr(emlAddr);
		account.setEnpswd(null); // 소셜 = 비밀번호 없음
		account.setUserTypeCd(USER_TYPE_MEMBER);
		account.setLgnType(LGN_TYPE_SOCIAL);
		account.setAcntStts(SIGNUP_STTS);
		account.setUserNm(userNm);
		// provider email_verified=true 면 가입 시점에 즉시 검증 처리(인증코드 미발송) — 출처=provider 코드(예: GOOGLE).
		if (emailVerified) {
			account.setEmlVrfdYn("Y");
			account.setEmlVrfcSrc(prvdrCd);
		} else {
			account.setEmlVrfdYn("N");
		}
		try {
			accountMapper.insertAccount(account);
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 동시 가입 경합 — DB 활성유니크 최종 방어
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE,
					AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE.defaultMessage(), e);
		}
		long userSn = account.getUserSn();

		// 불변식: 소셜 가입도 같은 트랜잭션에서 USER_DTL_INFO 1:1 행을 반드시 생성한다(프로필 교체가 의존).
		accountMapper.insertUserDtlInfo(userSn);
		accountMapper.insertUserSocialAcnt(userSn, prvdrCd, prvdrUserId, emlAddr);
		recordTermsAgreements(accountMapper, userSn, trmsAgreList, agreChnlCd, cntnIp, userAgnt);
		assignRole(accountMapper, userSn, memberDefaultRoleCd);
		return userSn;
	}

	// ------------------------------------------------------------------
	// 가입 공통 로직 (파트너 가입(PartnerServiceImpl)과 공유 — 같은 도메인 내 static 헬퍼)
	// ------------------------------------------------------------------

	/** 시행 중 필수약관 전부에 Y 동의했는지 + 요청 약관이 실재·중복 없는지 검증. */
	public static void validateEssentialTerms(List<TrmsVO> effectiveTerms, List<TrmsAgreItem> agreList) {
		List<TrmsVO> activeTrms = effectiveTerms;
		Map<Long, TrmsVO> activeBySn = activeTrms.stream()
				.collect(Collectors.toMap(TrmsVO::getTrmsSn, t -> t));

		// 같은 약관에 Y/N 을 함께 보내 필수검증을 우회하는 것 차단 (codex 필수수정 1)
		Set<Long> seen = new java.util.HashSet<>();
		for (TrmsAgreItem item : agreList) {
			if (!seen.add(item.trmsSnAsLong())) {
				throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "중복된 약관 동의 항목입니다.");
			}
			if (!activeBySn.containsKey(item.trmsSnAsLong())) {
				throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "존재하지 않거나 시행 중이 아닌 약관입니다.");
			}
		}

		Set<Long> agreedSns = agreList.stream()
				.filter(TrmsAgreItem::isAgreed)
				.map(TrmsAgreItem::trmsSnAsLong)
				.collect(Collectors.toSet());
		boolean essentialMissing = activeTrms.stream()
				.filter(t -> "Y".equals(t.getEsntlYn()))
				.anyMatch(t -> !agreedSns.contains(t.getTrmsSn()));
		if (essentialMissing) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_TERMS_REQUIRED);
		}
	}

	/** 약관 동의/철회 이력 기록 (Y/N 모두 — append-only 증적). */
	public static void recordTermsAgreements(AccountMapper accountMapper, long userSn, List<TrmsAgreItem> agreList,
			String agreChnlCd, String cntnIp, String userAgnt) {
		for (TrmsAgreItem item : agreList) {
			accountMapper.insertTrmsAgre(userSn, item.trmsSnAsLong(), item.agreYn(), agreChnlCd, cntnIp, userAgnt);
		}
	}

	/** 기본 역할 배정 — 역할 데이터 부재는 시드/설정 불일치(운영 오류). */
	public static void assignRole(AccountMapper accountMapper, long userSn, String roleCd) {
		Long roleSn = accountMapper.selectRoleSnByRoleCd(roleCd);
		if (roleSn == null) {
			log.error("[ACCOUNT] default role not found in ROLE table. roleCd={}", roleCd);
			throw new BusinessException(AccountErrorCode.ACCOUNT_ROLE_NOT_CONFIGURED);
		}
		accountMapper.insertUserRole(userSn, roleSn);
	}
}
