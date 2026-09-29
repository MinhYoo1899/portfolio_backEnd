package egovframework.eeum.account.service.operator.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.account.vo.OperatorVO;
import egovframework.eeum.account.service.operator.OperatorService;
import egovframework.eeum.account.service.operator.OperatorSetupTokenStore;
import egovframework.eeum.account.service.operator.dao.OperatorMapper;
import egovframework.eeum.account.web.dto.OperatorCreatedResponse;
import egovframework.eeum.account.web.dto.OperatorDetailResponse;
import egovframework.eeum.account.web.dto.OperatorInsertRequest;
import egovframework.eeum.account.web.dto.OperatorListItemResponse;
import egovframework.eeum.account.web.dto.OperatorMeResponse;
import egovframework.eeum.account.web.dto.OperatorPasswordResetRequest;
import egovframework.eeum.account.web.dto.OperatorRole;
import egovframework.eeum.account.web.dto.OperatorRoleChangeRequest;
import egovframework.eeum.account.web.dto.OperatorSearchRequest;
import egovframework.eeum.account.web.dto.OperatorSetupPasswordRequest;
import egovframework.eeum.account.web.dto.OperatorStatusChangeRequest;
import egovframework.eeum.account.web.dto.OperatorUpdateRequest;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.crypto.FieldHasher;
import egovframework.eeum.base.crypto.PhoneNumbers;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.base.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영자 계정관리 서비스 구현.
 *
 * <p>역할코드는 {@link OperatorRole}(STAFF/SUPERVISOR) enum 으로만 매핑 — 요청 body 의 raw ROLE_CD/AUTH_CD
 * 신뢰 금지. SUPER 는 이 API 에서 부여/회수/수정 대상이 될 수 없다(보호). 상태/역할/비번초기화는 대상행 FOR UPDATE
 * 로 직렬화해 "마지막 가용 SUPERVISOR 0명" 경합을 race-safe 하게 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorServiceImpl implements OperatorService {

	private static final String USER_TYPE_OPERATOR = "OPERATOR";
	private static final String LGN_TYPE_EMAIL = "EMAIL";
	private static final String SIGNUP_STTS = "PENDING";
	private static final String STTS_ACTIVE = "ACTIVE";
	private static final String STTS_SUSPENDED = "SUSPENDED";

	private static final String ROLE_STAFF = "OPERATOR_STAFF";
	private static final String ROLE_SUPERVISOR = "OPERATOR_SUPERVISOR";
	private static final String ROLE_SUPER = "SUPER";

	private final OperatorMapper operatorMapper;
	private final AccountMapper accountMapper;
	private final OperatorSetupTokenStore setupTokenStore;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final AuthRevocationStore authRevocationStore;
	private final TokenSessionStore tokenSessionStore;
	private final AuditRecorder auditRecorder;
	private final FieldHasher fieldHasher;

	/** 설정 URL 베이스 — 발급 토큰을 붙여 운영자가 비밀번호를 설정하는 화면. */
	@Value("${eeum.operator.setup.url-base:http://localhost:5173/operator/setup-password}")
	private String setupUrlBase;

	// ------------------------------------------------------------------ 생성
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_INSERT", trgtType = "USER_ACNT", trgtId = "#result.userSn()")
	public OperatorCreatedResponse insert(OperatorInsertRequest request, long actorUserSn) {
		if (accountMapper.existsActiveEmlAddr(request.emlAddr()) > 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE);
		}
		OperatorRole role = request.role();
		if (role == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_INVALID_ROLE);
		}

		// 1) 계정 — 비번 미설정(NULL), PENDING, 이메일=로그인ID. USER_TYPE_CD 는 서버가 OPERATOR 고정.
		AccountVO account = new AccountVO();
		account.setEmlAddr(request.emlAddr());
		account.setEnpswd(null); // 설정토큰으로 본인이 설정
		account.setUserTypeCd(USER_TYPE_OPERATOR);
		account.setLgnType(LGN_TYPE_EMAIL);
		account.setAcntStts(SIGNUP_STTS);
		account.setUserNm(request.userNm());
		// 전화번호(필수) — 정규화(§3) 후 암호문(TypeHandler)·검색해시(HMAC)를 계정 insert 에 함께 세팅(원자적, MDFCN_DT=REG_DT 보존).
		// 키 미설정 시 FieldHasher 가 fail-closed(FieldCryptoException) — 해시 없이 평문 저장 우회 차단.
		String normalizedPhone = PhoneNumbers.normalizeMobile(request.phone());
		account.setMblTelno(normalizedPhone);
		account.setMblTelnoHash(fieldHasher.hmacHex(normalizedPhone));
		try {
			accountMapper.insertAccount(account);
		} catch (org.springframework.dao.DuplicateKeyException e) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE,
					AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE.defaultMessage(), e);
		}
		long userSn = account.getUserSn();

		// 2) 운영자 상세 1:1
		operatorMapper.insertOperatorDtlInfo(userSn, request.deptNm(), request.jbpsNm());

		// 3) 역할 — SUPERVISOR 면 STAFF 동시부여(동시부여 불변식). 허용역할만(enum 보장).
		grantRoleWithImpliedStaff(userSn, role);

		// 4) 설정토큰 1회 발급(아직 토큰 미사용 — 세션 무효화 불필요: 로그인 가능한 세션이 없음)
		String token = setupTokenStore.issueToken(userSn);
		log.info("[OPERATOR] created. userSn={}, role={}, actor={}", userSn, role, actorUserSn);
		return OperatorCreatedResponse.of(userSn, token, setupUrl(token));
	}

	// ------------------------------------------------------------------ 목록/상세
	@Override
	@Transactional(readOnly = true)
	public PageResponse<OperatorListItemResponse> list(OperatorSearchRequest request) {
		long total = operatorMapper.countOperatorList(request);
		List<OperatorListItemResponse> items = operatorMapper.selectOperatorList(request).stream()
				.map(vo -> OperatorListItemResponse.from(vo, operatorMapper.selectRoleCdsByUserSn(vo.getUserSn())))
				.toList();
		// 대량 열람 — 검색조건/건수 명시 기록(PartnerServiceImpl.list 패턴)
		auditRecorder.recordSuccess(
				String.format("OPERATOR_LIST_VIEW {\"page\":%d,\"size\":%d,\"acntStts\":%s,\"role\":%s,\"resultCount\":%d}",
						request.getPage(), request.getPageSize(),
						request.getAcntStts() == null ? "null" : "\"" + request.getAcntStts() + "\"",
						request.getRole() == null ? "null" : "\"" + request.getRole() + "\"",
						items.size()),
				"USER_ACNT", null);
		return PageResponse.of(items, total, request.getPage(), request.getPageSize());
	}

	@Override
	@Transactional(readOnly = true)
	@Audit(actn = "OPERATOR_DETAIL_VIEW", trgtType = "USER_ACNT", trgtId = "#userSn")
	public OperatorDetailResponse detail(long userSn) {
		OperatorVO operator = operatorMapper.selectOperatorByUserSn(userSn);
		if (operator == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
		}
		return OperatorDetailResponse.from(operator, operatorMapper.selectRoleCdsByUserSn(userSn));
	}

	// ------------------------------------------------------------------ 상세 수정(부서/직위/연락처)
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_UPDATE", trgtType = "USER_ACNT", trgtId = "#request.userSn()")
	public void update(OperatorUpdateRequest request, long actorUserSn) {
		long targetUserSn = request.userSnAsLong();
		boolean hasDept = request.deptNm() != null;
		boolean hasJbps = request.jbpsNm() != null;
		boolean hasPhone = request.phone() != null && !request.phone().isBlank();
		if (!hasDept && !hasJbps && !hasPhone) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "수정할 내용이 없습니다.");
		}
		// 대상 OPERATOR 한정(404). 부서/직위/연락처는 "기본정보" 라 SUPER 보호를 적용하지 않는다
		// (SUPER 보호는 역할/상태/비번초기화 같은 보안 제어 전용 — changeRole/changeStatus/resetPassword).
		if (operatorMapper.selectOperatorByUserSn(targetUserSn) == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
		}
		if (hasDept || hasJbps) {
			// 0행 = 대상 상세행 유실(동시삭제 등) → 성공으로 숨기지 말고 대상 유실 오류(404)
			if (operatorMapper.updateOperatorDtlInfo(targetUserSn, request.deptNm(), request.jbpsNm(), actorUserSn) == 0) {
				throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
			}
		}
		if (hasPhone) {
			// 정규화(§3) 후 암호문·해시 동시 갱신(같은 트랜잭션). 생략 시엔 이 경로를 타지 않아 기존 번호 보존.
			String normalized = PhoneNumbers.normalizeMobile(request.phone());
			if (operatorMapper.updateOperatorPhone(targetUserSn, normalized, fieldHasher.hmacHex(normalized), actorUserSn) == 0) {
				throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
			}
		}
		// 로그에 전화번호 평문 미포함(변경 여부 boolean 만) — crypto-standard §7
		log.info("[OPERATOR] info updated. target={}, dept={}, jbps={}, phone={}, actor={}",
				targetUserSn, hasDept, hasJbps, hasPhone, actorUserSn);
	}

	// ------------------------------------------------------------------ 본인 정보 조회
	@Override
	@Transactional(readOnly = true)
	public OperatorMeResponse me(long userSn) {
		OperatorVO operator = operatorMapper.selectOperatorByUserSn(userSn);
		if (operator == null) {
			// 비OPERATOR(회원 등) 토큰이 접근한 경우 포함 — "없는 것처럼" 404
			throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
		}
		return OperatorMeResponse.from(operator, operatorMapper.selectRoleCdsByUserSn(userSn));
	}

	// ------------------------------------------------------------------ 역할 변경
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_ROLE_CHANGE", trgtType = "USER_ACNT", trgtId = "#request.userSn()")
	public void changeRole(OperatorRoleChangeRequest request, long actorUserSn) {
		long targetUserSn = request.userSnAsLong();
		OperatorRole newRole = request.role();
		if (newRole == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_INVALID_ROLE);
		}
		// 행 잠금 + OPERATOR 검증 + 자기 자신/ SUPER 보호
		requireOperatorForMutation(targetUserSn, actorUserSn);
		List<String> currentRoles = operatorMapper.selectRoleCdsByUserSn(targetUserSn);

		if (newRole == OperatorRole.SUPERVISOR) {
			// 승격 — SUPERVISOR 부여 + STAFF 동시 유지(부족하면 보강)
			grantRoleWithImpliedStaff(targetUserSn, OperatorRole.SUPERVISOR);
		} else {
			// 강등(STAFF) — 마지막 가용 SUPERVISOR 보호(대상이 SUPERVISOR 였다면)
			if (currentRoles.contains(ROLE_SUPERVISOR)) {
				guardLastActiveSupervisor(targetUserSn);
				operatorMapper.softDeleteUserRoleByRoleCd(targetUserSn, ROLE_SUPERVISOR, actorUserSn);
			}
			// STAFF 잔류(없으면 부여)
			grantRole(targetUserSn, ROLE_STAFF);
			// 강등 = 권한 축소 → 세션 무효화(즉시 반영)
			authRevocationStore.bumpGeneration(targetUserSn);
			tokenSessionStore.revokeAll(targetUserSn);
		}
		log.info("[OPERATOR] role changed. target={}, newRole={}, actor={}", targetUserSn, newRole, actorUserSn);
	}

	// ------------------------------------------------------------------ 상태 변경
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_STATUS_CHANGE", trgtType = "USER_ACNT", trgtId = "#request.userSn()")
	public void changeStatus(OperatorStatusChangeRequest request, long actorUserSn) {
		long targetUserSn = request.userSnAsLong();
		String newStts = request.acntStts();

		if (STTS_SUSPENDED.equals(newStts)) {
			// 비활성·잠금 — 자기 자신 금지 + SUPER 보호 + 마지막 SUPERVISOR 보호
			OperatorVO target = requireOperatorForMutation(targetUserSn, actorUserSn);
			// 마지막 가용 SUPERVISOR 보호(대상이 ACTIVE 수퍼바이저면 비활성으로 0명 되는 것 차단 — race-safe)
			guardLastActiveSupervisor(targetUserSn);
			int updated = operatorMapper.updateOperatorSttsIfCurrent(
					targetUserSn, target.getAcntStts(), STTS_SUSPENDED, actorUserSn);
			if (updated == 0) {
				throw new BusinessException(AccountErrorCode.OPERATOR_STATUS_NOT_ALLOWED);
			}
			// 로그인 차단 즉시 반영 — 세션 무효화
			authRevocationStore.bumpGeneration(targetUserSn);
			tokenSessionStore.revokeAll(targetUserSn);
		} else {
			// 활성화 — SUSPENDED→ACTIVE 만(PENDING 은 비번설정 흐름으로). 자기 자신 활성화는 무해하나 SUPER 보호는 유지.
			OperatorVO target = requireOperatorForMutationAllowSelf(targetUserSn);
			int updated = operatorMapper.updateOperatorSttsIfCurrent(
					targetUserSn, STTS_SUSPENDED, STTS_ACTIVE, actorUserSn);
			if (updated == 0) {
				throw new BusinessException(AccountErrorCode.OPERATOR_STATUS_NOT_ALLOWED);
			}
			log.info("[OPERATOR] activated. target={}, prevStts={}, actor={}",
					targetUserSn, target.getAcntStts(), actorUserSn);
		}
	}

	// ------------------------------------------------------------------ 비밀번호 초기화
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_PASSWORD_RESET", trgtType = "USER_ACNT", trgtId = "#request.userSn()")
	public OperatorCreatedResponse resetPassword(OperatorPasswordResetRequest request, long actorUserSn) {
		long targetUserSn = request.userSnAsLong();
		requireOperatorForMutation(targetUserSn, actorUserSn);
		// 마지막 가용 SUPERVISOR 보호 — 초기화는 대상을 ACTIVE 에서 떼어내므로(아래), 유일 ACTIVE 수퍼바이저면 거부.
		guardLastActiveSupervisor(targetUserSn);
		// 초기화 = 계정을 PENDING+ENPSWD NULL 로 되돌림(옛 비밀번호 즉시 무효 — 토큰 보유자만 새 비번 설정).
		// SUSPENDED 등은 0건 → 먼저 활성화 필요(잠금 우회 금지).
		int reset = operatorMapper.updateOperatorResetToPending(targetUserSn, actorUserSn);
		if (reset == 0) {
			throw new BusinessException(AccountErrorCode.OPERATOR_STATUS_NOT_ALLOWED);
		}
		// 설정토큰 재발급(이전 토큰 무효화) — 토큰 원문은 응답 1회 노출
		String token = setupTokenStore.issueToken(targetUserSn);
		// 기존 세션 즉시 무효화(generation bump + refresh 폐기) — 옛 자격으로의 접근 차단.
		authRevocationStore.bumpGeneration(targetUserSn);
		tokenSessionStore.revokeAll(targetUserSn);
		log.info("[OPERATOR] password reset (reverted to PENDING, token reissued). target={}, actor={}", targetUserSn, actorUserSn);
		return OperatorCreatedResponse.of(targetUserSn, token, setupUrl(token));
	}

	// ------------------------------------------------------------------ 비밀번호 설정(비인증)
	@Override
	@Transactional
	@Audit(actn = "OPERATOR_SETUP_PASSWORD", trgtType = "USER_ACNT", trgtId = "#result")
	public Long setupPassword(OperatorSetupPasswordRequest request) {
		// 정책 검증을 토큰 소비보다 먼저 — 약한 비밀번호가 1회성 토큰을 소진하지 않게(재발급 강요 방지).
		passwordPolicy.validate(request.newPassword());
		// 토큰 소비(원자 1회성) — 무효면 generic 400(존재 노출 없이). 실패 감사에는 토큰 원문 미기록(trgtId 만).
		Long targetUserSn = setupTokenStore.consume(request.token());
		if (targetUserSn == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_SETUP_TOKEN_INVALID);
		}
		int updated = operatorMapper.updateOperatorPasswordAndActivate(
				targetUserSn, passwordEncoder.encode(request.newPassword()));
		if (updated == 0) {
			// 토큰은 유효했으나 계정이 설정 대상 아님(이미 ACTIVE/탈퇴/비OPERATOR) — 토큰은 이미 소비됨.
			throw new BusinessException(AccountErrorCode.OPERATOR_SETUP_TOKEN_INVALID);
		}
		log.info("[OPERATOR] password set + activated. target={}", targetUserSn);
		return targetUserSn;
	}

	// ------------------------------------------------------------------ 공통 가드/헬퍼

	/**
	 * 변경 대상 운영자 잠금 조회 + 불변식: OPERATOR 한정(아니면 404), 자기 자신 금지(403), SUPER 보호(403).
	 */
	private OperatorVO requireOperatorForMutation(long targetUserSn, long actorUserSn) {
		if (targetUserSn == actorUserSn) {
			throw new BusinessException(AccountErrorCode.OPERATOR_CANNOT_MODIFY_SELF);
		}
		return lockOperatorAndGuardSuper(targetUserSn);
	}

	/** 자기 자신 허용(활성화 등) — OPERATOR 한정 + SUPER 보호만. */
	private OperatorVO requireOperatorForMutationAllowSelf(long targetUserSn) {
		return lockOperatorAndGuardSuper(targetUserSn);
	}

	private OperatorVO lockOperatorAndGuardSuper(long targetUserSn) {
		OperatorVO target = operatorMapper.selectOperatorForUpdate(targetUserSn);
		if (target == null) {
			throw new BusinessException(AccountErrorCode.OPERATOR_NOT_FOUND);
		}
		if (operatorMapper.selectRoleCdsByUserSn(targetUserSn).contains(ROLE_SUPER)) {
			throw new BusinessException(AccountErrorCode.OPERATOR_SUPER_PROTECTED);
		}
		return target;
	}

	/**
	 * 마지막 가용 SUPERVISOR 보호(race-safe). ACTIVE 수퍼바이저 행집합을 FOR UPDATE 로 잠가, 서로 다른 두
	 * 수퍼바이저를 동시에 강등/비활성/초기화해도 둘 다 같은 행집합에서 직렬화되게 한다(0명 진입 차단). 대상이 그
	 * 집합의 유일 구성원이면 이 동작으로 가용 0명이 되므로 거부(409). 대상이 ACTIVE 수퍼바이저가 아니면
	 * (PENDING/SUSPENDED/비수퍼바이저) 이 동작은 가용 수를 줄이지 않으므로 통과.
	 */
	private void guardLastActiveSupervisor(long targetUserSn) {
		List<Long> activeSupervisors = operatorMapper.selectActiveSupervisorUserSnsForUpdate();
		if (activeSupervisors.size() == 1 && activeSupervisors.contains(targetUserSn)) {
			throw new BusinessException(AccountErrorCode.OPERATOR_LAST_SUPERVISOR);
		}
	}

	/** SUPERVISOR 부여(+STAFF 동시부여) 또는 STAFF 부여. enum 으로 허용역할 보장. */
	private void grantRoleWithImpliedStaff(long userSn, OperatorRole role) {
		if (role == OperatorRole.SUPERVISOR) {
			grantRole(userSn, ROLE_SUPERVISOR);
			grantRole(userSn, ROLE_STAFF); // 동시부여 불변식
		} else {
			grantRole(userSn, ROLE_STAFF);
		}
	}

	/** 단일 역할코드 부여(멱등) — ROLE_MST 데이터 부재는 시드/설정 불일치(운영 오류). */
	private void grantRole(long userSn, String roleCd) {
		Long roleSn = operatorMapper.selectRoleSnByRoleCd(roleCd);
		if (roleSn == null) {
			log.error("[OPERATOR] role not found in ROLE_MST. roleCd={}", roleCd);
			throw new BusinessException(AccountErrorCode.ACCOUNT_ROLE_NOT_CONFIGURED);
		}
		operatorMapper.insertUserRoleIfAbsent(userSn, roleSn);
	}

	private String setupUrl(String token) {
		// url-base 미설정(또는 빈값)이면 URL 은 생략하고 토큰만 노출(클라가 자체 조립). 방어적 null-safe.
		if (setupUrlBase == null || setupUrlBase.isBlank()) {
			return null;
		}
		String sep = setupUrlBase.contains("?") ? "&" : "?";
		return setupUrlBase + sep + "token=" + token;
	}
}
