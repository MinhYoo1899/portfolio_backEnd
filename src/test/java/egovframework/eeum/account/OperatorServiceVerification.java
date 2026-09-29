package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.operator.OperatorSetupTokenStore;
import egovframework.eeum.account.service.operator.dao.OperatorMapper;
import egovframework.eeum.account.service.operator.impl.OperatorServiceImpl;
import egovframework.eeum.account.vo.OperatorVO;
import egovframework.eeum.account.web.dto.OperatorInsertRequest;
import egovframework.eeum.account.web.dto.OperatorPasswordResetRequest;
import egovframework.eeum.account.web.dto.OperatorRole;
import egovframework.eeum.account.web.dto.OperatorRoleChangeRequest;
import egovframework.eeum.account.web.dto.OperatorSetupPasswordRequest;
import egovframework.eeum.account.web.dto.OperatorStatusChangeRequest;
import egovframework.eeum.account.web.dto.OperatorUpdateRequest;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.TokenSessionStore;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.crypto.FieldHasher;
import egovframework.eeum.base.crypto.FieldHmacKeyProvider;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.security.PasswordPolicy;

/**
 * 운영자 계정관리 서비스 검증(DB/Redis 없이 mock) — 핵심 보안 불변식:
 * 비OPERATOR수정불가 / SUPERVISOR부여시STAFF동시 / 자기강등·비활성불가 / 마지막SUPERVISOR보호 /
 * SUPER부여불가 / setup토큰소비후PENDING→ACTIVE / 토큰무효화호출.
 */
class OperatorServiceVerification {

	private static final long ACTOR = 99L;
	private static final long TARGET = 12L;

	private final OperatorMapper operatorMapper = mock(OperatorMapper.class);
	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final OperatorSetupTokenStore setupTokenStore = mock(OperatorSetupTokenStore.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
	private final AuthRevocationStore authRevocationStore = mock(AuthRevocationStore.class);
	private final TokenSessionStore tokenSessionStore = mock(TokenSessionStore.class);
	private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
	// 실 FieldHasher(테스트 전용 키) — 정규화/해시 로직을 실제 수행(암호화 TypeHandler 는 매퍼 mock 이라 미개입).
	private final FieldHasher fieldHasher = new FieldHasher(
			new FieldHmacKeyProvider(java.util.Base64.getEncoder().encodeToString(new byte[32])));

	private final OperatorServiceImpl service = new OperatorServiceImpl(
			operatorMapper, accountMapper, setupTokenStore, passwordEncoder, passwordPolicy,
			authRevocationStore, tokenSessionStore, auditRecorder, fieldHasher);

	private static OperatorVO operator(long userSn, String acntStts) {
		OperatorVO vo = new OperatorVO();
		vo.setUserSn(userSn);
		vo.setAcntStts(acntStts);
		return vo;
	}

	// ---------------------------------------------------------------- 생성
	@Test
	@DisplayName("생성 — SUPERVISOR 부여 시 STAFF 동시부여 + 설정토큰 발급(아직 세션 무효화 없음)")
	void insertSupervisorGrantsStaffToo() {
		when(accountMapper.existsActiveEmlAddr("op@eeum.kr")).thenReturn(0);
		when(operatorMapper.selectRoleSnByRoleCd("OPERATOR_SUPERVISOR")).thenReturn(2L);
		when(operatorMapper.selectRoleSnByRoleCd("OPERATOR_STAFF")).thenReturn(1L);
		when(setupTokenStore.issueToken(anyLong())).thenReturn("10.tok");
		// insertAccount 후 generated key 세팅 모사
		when(accountMapper.insertAccount(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
			inv.getArgument(0, egovframework.eeum.account.vo.AccountVO.class).setUserSn(10L);
			return 1;
		});

		var req = new OperatorInsertRequest("op@eeum.kr", "운영자", OperatorRole.SUPERVISOR, "운영팀", "사원", "010-1234-5678");
		service.insert(req, ACTOR);

		verify(operatorMapper).insertUserRoleIfAbsent(10L, 2L); // SUPERVISOR
		verify(operatorMapper).insertUserRoleIfAbsent(10L, 1L); // STAFF 동시부여
		verify(setupTokenStore).issueToken(10L);
		verify(authRevocationStore, never()).bumpGeneration(anyLong()); // 신규=세션 없음
	}

	@Test
	@DisplayName("생성 — 이메일 중복이면 ACCOUNT_EMAIL_DUPLICATE, 계정/역할/토큰 생성 안 함")
	void insertDuplicateEmail() {
		when(accountMapper.existsActiveEmlAddr("dup@eeum.kr")).thenReturn(1);

		var req = new OperatorInsertRequest("dup@eeum.kr", "운영자", OperatorRole.STAFF, null, null, "01000000000");
		BusinessException e = assertThrows(BusinessException.class, () -> service.insert(req, ACTOR));
		assertEquals(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE, e.getErrorCode());
		verify(accountMapper, never()).insertAccount(org.mockito.ArgumentMatchers.any());
		verify(setupTokenStore, never()).issueToken(anyLong());
	}

	// ---------------------------------------------------------------- 비OPERATOR / SUPER 보호
	@Test
	@DisplayName("상태변경 — 대상이 OPERATOR 아니면(잠금조회 null) OPERATOR_NOT_FOUND")
	void statusNonOperatorNotFound() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(null);

		var req = new OperatorStatusChangeRequest(String.valueOf(TARGET), "SUSPENDED");
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeStatus(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
		verify(operatorMapper, never()).updateOperatorSttsIfCurrent(anyLong(), anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("역할변경 — 대상이 SUPER 보유면 OPERATOR_SUPER_PROTECTED, 변경 안 함")
	void roleSuperProtected() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_SUPERVISOR", "SUPER"));

		var req = new OperatorRoleChangeRequest(String.valueOf(TARGET), OperatorRole.STAFF);
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeRole(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_SUPER_PROTECTED, e.getErrorCode());
		verify(operatorMapper, never()).softDeleteUserRoleByRoleCd(anyLong(), anyString(), anyLong());
	}

	// ---------------------------------------------------------------- 자기 자신 보호
	@Test
	@DisplayName("상태변경(비활성) — 자기 자신이면 OPERATOR_CANNOT_MODIFY_SELF")
	void suspendSelfForbidden() {
		var req = new OperatorStatusChangeRequest(String.valueOf(ACTOR), "SUSPENDED");
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeStatus(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_CANNOT_MODIFY_SELF, e.getErrorCode());
		verify(operatorMapper, never()).selectOperatorForUpdate(anyLong());
	}

	@Test
	@DisplayName("역할변경(강등) — 자기 자신이면 OPERATOR_CANNOT_MODIFY_SELF")
	void demoteSelfForbidden() {
		var req = new OperatorRoleChangeRequest(String.valueOf(ACTOR), OperatorRole.STAFF);
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeRole(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_CANNOT_MODIFY_SELF, e.getErrorCode());
	}

	// ---------------------------------------------------------------- 마지막 SUPERVISOR 보호
	@Test
	@DisplayName("역할변경(강등) — 강등 시 가용 SUPERVISOR 0명이 되면 OPERATOR_LAST_SUPERVISOR")
	void demoteLastSupervisorBlocked() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_SUPERVISOR", "OPERATOR_STAFF"));
		// 가용 ACTIVE 수퍼바이저가 대상 1명뿐(행 잠금 집합) → 강등 시 0명 → 차단
		when(operatorMapper.selectActiveSupervisorUserSnsForUpdate()).thenReturn(List.of(TARGET));

		var req = new OperatorRoleChangeRequest(String.valueOf(TARGET), OperatorRole.STAFF);
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeRole(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_LAST_SUPERVISOR, e.getErrorCode());
		verify(operatorMapper, never()).softDeleteUserRoleByRoleCd(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("상태변경(비활성) — 마지막 SUPERVISOR 비활성화 시 OPERATOR_LAST_SUPERVISOR")
	void suspendLastSupervisorBlocked() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_SUPERVISOR", "OPERATOR_STAFF"));
		when(operatorMapper.selectActiveSupervisorUserSnsForUpdate()).thenReturn(List.of(TARGET));

		var req = new OperatorStatusChangeRequest(String.valueOf(TARGET), "SUSPENDED");
		BusinessException e = assertThrows(BusinessException.class, () -> service.changeStatus(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_LAST_SUPERVISOR, e.getErrorCode());
		verify(operatorMapper, never()).updateOperatorSttsIfCurrent(anyLong(), anyString(), anyString(), anyLong());
	}

	// ---------------------------------------------------------------- 역할변경 정상(승격/강등)
	@Test
	@DisplayName("역할변경(승격) — SUPERVISOR 부여 + STAFF 동시 유지")
	void promoteGrantsSupervisorAndStaff() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_STAFF"));
		when(operatorMapper.selectRoleSnByRoleCd("OPERATOR_SUPERVISOR")).thenReturn(2L);
		when(operatorMapper.selectRoleSnByRoleCd("OPERATOR_STAFF")).thenReturn(1L);

		var req = new OperatorRoleChangeRequest(String.valueOf(TARGET), OperatorRole.SUPERVISOR);
		service.changeRole(req, ACTOR);

		verify(operatorMapper).insertUserRoleIfAbsent(TARGET, 2L);
		verify(operatorMapper).insertUserRoleIfAbsent(TARGET, 1L);
	}

	@Test
	@DisplayName("역할변경(강등) — SUPERVISOR 해제 + STAFF 잔류 + 세션 무효화")
	void demoteRevokesSupervisorKeepsStaff() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_SUPERVISOR", "OPERATOR_STAFF"));
		// 가용 ACTIVE 수퍼바이저가 대상 외에도 존재(행집합 size>1) → 강등 허용
		when(operatorMapper.selectActiveSupervisorUserSnsForUpdate()).thenReturn(List.of(TARGET, 5L, 6L));
		when(operatorMapper.selectRoleSnByRoleCd("OPERATOR_STAFF")).thenReturn(1L);

		var req = new OperatorRoleChangeRequest(String.valueOf(TARGET), OperatorRole.STAFF);
		service.changeRole(req, ACTOR);

		verify(operatorMapper).softDeleteUserRoleByRoleCd(TARGET, "OPERATOR_SUPERVISOR", ACTOR);
		verify(operatorMapper).insertUserRoleIfAbsent(TARGET, 1L); // STAFF 잔류 보장
		verify(authRevocationStore).bumpGeneration(TARGET);
		verify(tokenSessionStore).revokeAll(TARGET);
	}

	// ---------------------------------------------------------------- 비활성 정상 + 세션 무효화
	@Test
	@DisplayName("상태변경(비활성) — 정상 시 SUSPENDED 전이 + 세션 무효화")
	void suspendSuccessRevokesSessions() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_STAFF"));
		when(operatorMapper.updateOperatorSttsIfCurrent(TARGET, "ACTIVE", "SUSPENDED", ACTOR)).thenReturn(1);

		var req = new OperatorStatusChangeRequest(String.valueOf(TARGET), "SUSPENDED");
		service.changeStatus(req, ACTOR);

		verify(operatorMapper).updateOperatorSttsIfCurrent(TARGET, "ACTIVE", "SUSPENDED", ACTOR);
		verify(authRevocationStore).bumpGeneration(TARGET);
		verify(tokenSessionStore).revokeAll(TARGET);
	}

	// ---------------------------------------------------------------- 비밀번호 초기화 = 세션 무효화
	@Test
	@DisplayName("비번 초기화 — PENDING 되돌림 + 설정토큰 재발급 + 세션 무효화")
	void resetPasswordRevertsToPendingReissuesTokenAndRevokes() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_STAFF"));
		when(operatorMapper.updateOperatorResetToPending(TARGET, ACTOR)).thenReturn(1);
		when(setupTokenStore.issueToken(TARGET)).thenReturn("12.newtok");

		var req = new OperatorPasswordResetRequest(String.valueOf(TARGET));
		service.resetPassword(req, ACTOR);

		verify(operatorMapper).updateOperatorResetToPending(TARGET, ACTOR); // ACTIVE→PENDING+ENPSWD NULL
		verify(setupTokenStore).issueToken(TARGET);
		verify(authRevocationStore).bumpGeneration(TARGET);
		verify(tokenSessionStore).revokeAll(TARGET);
	}

	@Test
	@DisplayName("비번 초기화 — SUSPENDED 등 대상 아님(updated==0)이면 OPERATOR_STATUS_NOT_ALLOWED, 토큰 미발급")
	void resetPasswordNotEligible() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "SUSPENDED"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_STAFF"));
		when(operatorMapper.updateOperatorResetToPending(TARGET, ACTOR)).thenReturn(0);

		var req = new OperatorPasswordResetRequest(String.valueOf(TARGET));
		BusinessException e = assertThrows(BusinessException.class, () -> service.resetPassword(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_STATUS_NOT_ALLOWED, e.getErrorCode());
		verify(setupTokenStore, never()).issueToken(anyLong());
		verify(authRevocationStore, never()).bumpGeneration(anyLong());
	}

	@Test
	@DisplayName("비번 초기화 — 마지막 ACTIVE SUPERVISOR면 OPERATOR_LAST_SUPERVISOR(되돌림으로 가용 0명 차단), 토큰 미발급")
	void resetPasswordLastSupervisorBlocked() {
		when(operatorMapper.selectOperatorForUpdate(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_SUPERVISOR", "OPERATOR_STAFF"));
		when(operatorMapper.selectActiveSupervisorUserSnsForUpdate()).thenReturn(List.of(TARGET));

		var req = new OperatorPasswordResetRequest(String.valueOf(TARGET));
		BusinessException e = assertThrows(BusinessException.class, () -> service.resetPassword(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_LAST_SUPERVISOR, e.getErrorCode());
		verify(operatorMapper, never()).updateOperatorResetToPending(anyLong(), anyLong());
		verify(setupTokenStore, never()).issueToken(anyLong());
	}

	@Test
	@DisplayName("비번 초기화 — 자기 자신이면 OPERATOR_CANNOT_MODIFY_SELF")
	void resetPasswordSelfForbidden() {
		var req = new OperatorPasswordResetRequest(String.valueOf(ACTOR));
		BusinessException e = assertThrows(BusinessException.class, () -> service.resetPassword(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_CANNOT_MODIFY_SELF, e.getErrorCode());
		verify(setupTokenStore, never()).issueToken(anyLong());
	}

	// ---------------------------------------------------------------- 비밀번호 설정(비인증)
	@Test
	@DisplayName("비번 설정 — 토큰 소비 후 ENPSWD 설정 + PENDING→ACTIVE")
	void setupPasswordConsumesTokenAndActivates() {
		when(setupTokenStore.consume("12.tok")).thenReturn(TARGET);
		when(passwordEncoder.encode("newPw1!")).thenReturn("ENC");
		when(operatorMapper.updateOperatorPasswordAndActivate(TARGET, "ENC")).thenReturn(1);

		var req = new OperatorSetupPasswordRequest("12.tok", "newPw1!");
		Long result = service.setupPassword(req);

		assertEquals(TARGET, result); // 감사 trgtId 용 — 설정된 userSn 반환
		verify(passwordPolicy).validate("newPw1!");
		verify(operatorMapper).updateOperatorPasswordAndActivate(TARGET, "ENC");
	}

	@Test
	@DisplayName("비번 설정 — 토큰 무효면 OPERATOR_SETUP_TOKEN_INVALID, 갱신 안 함(정책 검증은 토큰 소비 전에 수행)")
	void setupPasswordInvalidToken() {
		when(setupTokenStore.consume("bad")).thenReturn(null);

		var req = new OperatorSetupPasswordRequest("bad", "newPw1!");
		BusinessException e = assertThrows(BusinessException.class, () -> service.setupPassword(req));
		assertEquals(AccountErrorCode.OPERATOR_SETUP_TOKEN_INVALID, e.getErrorCode());
		verify(operatorMapper, never()).updateOperatorPasswordAndActivate(anyLong(), anyString());
		// 정책 검증을 토큰 소비보다 먼저 — 약한 비번이 토큰을 소진하지 않게(여기선 통과 후 토큰 무효로 실패).
		verify(passwordPolicy).validate("newPw1!");
	}

	@Test
	@DisplayName("비번 설정 — 토큰 유효하나 계정이 설정대상 아님(updated==0)이면 OPERATOR_SETUP_TOKEN_INVALID")
	void setupPasswordTokenValidButAccountNotEligible() {
		when(setupTokenStore.consume("12.tok")).thenReturn(TARGET);
		when(passwordEncoder.encode("newPw1!")).thenReturn("ENC");
		when(operatorMapper.updateOperatorPasswordAndActivate(TARGET, "ENC")).thenReturn(0);

		var req = new OperatorSetupPasswordRequest("12.tok", "newPw1!");
		BusinessException e = assertThrows(BusinessException.class, () -> service.setupPassword(req));
		assertEquals(AccountErrorCode.OPERATOR_SETUP_TOKEN_INVALID, e.getErrorCode());
	}

	// ---------------------------------------------------------------- 상세 비OPERATOR
	@Test
	@DisplayName("상세 — 대상이 OPERATOR 아니면 OPERATOR_NOT_FOUND")
	void detailNonOperator() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(null);

		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(TARGET));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
	}

	// ---------------------------------------------------------------- 연락처 노출: 상세=마스킹 / 본인=전체
	@Test
	@DisplayName("상세 — 연락처는 마스킹(010-****-5678)만 노출")
	void detailMasksPhone() {
		OperatorVO vo = operator(TARGET, "ACTIVE");
		vo.setMblTelno("01012345678"); // 매퍼가 복호화해 채운 평문 모사
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(vo);
		when(operatorMapper.selectRoleCdsByUserSn(TARGET)).thenReturn(List.of("OPERATOR_STAFF"));

		assertEquals("010-****-5678", service.detail(TARGET).mblTelnoMasked());
	}

	@Test
	@DisplayName("본인(me) — 연락처 전체 노출, 비OPERATOR(null)면 404")
	void meReturnsFullPhone() {
		OperatorVO vo = operator(ACTOR, "ACTIVE");
		vo.setMblTelno("01012345678");
		when(operatorMapper.selectOperatorByUserSn(ACTOR)).thenReturn(vo);
		when(operatorMapper.selectRoleCdsByUserSn(ACTOR)).thenReturn(List.of("OPERATOR_STAFF"));
		assertEquals("01012345678", service.me(ACTOR).mblTelno());

		when(operatorMapper.selectOperatorByUserSn(5L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.me(5L));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
	}

	// ---------------------------------------------------------------- 상세 수정(부서/직위/연락처)
	@Test
	@DisplayName("수정 — 전화번호는 정규화(§3, 하이픈/국가코드)·해시 동시 저장, 상세는 미갱신")
	void updateNormalizesAndHashesPhone() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.updateOperatorPhone(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), null, null, "+82 10-1234-5678");
		service.update(req, ACTOR);

		String expectedHash = fieldHasher.hmacHex("01012345678");
		verify(operatorMapper).updateOperatorPhone(TARGET, "01012345678", expectedHash, ACTOR);
		verify(operatorMapper, never()).updateOperatorDtlInfo(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("수정 — 전화번호 생략(null)이면 폰 미갱신(기존 보존), 부서/직위만 갱신")
	void updatePreservesPhoneWhenOmitted() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.updateOperatorDtlInfo(anyLong(), any(), any(), anyLong())).thenReturn(1);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), "신설팀", null, null);
		service.update(req, ACTOR);

		verify(operatorMapper).updateOperatorDtlInfo(TARGET, "신설팀", null, ACTOR);
		verify(operatorMapper, never()).updateOperatorPhone(anyLong(), anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("수정 — 변경할 내용이 전혀 없으면 VALIDATION_ERROR(대상 조회도 안 함)")
	void updateNoContentRejected() {
		var req = new OperatorUpdateRequest(String.valueOf(TARGET), null, null, null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.update(req, ACTOR));
		assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		verify(operatorMapper, never()).selectOperatorByUserSn(anyLong());
	}

	@Test
	@DisplayName("수정 — SUPER 운영자도 기본정보(연락처) 수정 성공(SUPER 보호는 보안제어 전용, 역할조회조차 안 함)")
	void updateSuperContactSucceeds() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.updateOperatorPhone(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), null, null, "010-1234-5678");
		service.update(req, ACTOR);

		verify(operatorMapper).updateOperatorPhone(TARGET, "01012345678", fieldHasher.hmacHex("01012345678"), ACTOR);
		// 기본정보 수정은 SUPER 여부를 조회하지 않는다(보안제어 전용 보호) — 역할 조회 자체가 없어야 함
		verify(operatorMapper, never()).selectRoleCdsByUserSn(anyLong());
	}

	@Test
	@DisplayName("수정 — 대상이 OPERATOR 아니면 OPERATOR_NOT_FOUND")
	void updateNonOperatorNotFound() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(null);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), "팀", null, null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.update(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("수정 — 상세행 유실(updateOperatorDtlInfo 0행)은 성공 숨기지 말고 대상 유실 404")
	void updateDtlInfoZeroRowsNotFound() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.updateOperatorDtlInfo(anyLong(), any(), any(), anyLong())).thenReturn(0);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), "팀", null, null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.update(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("수정 — 연락처 갱신 0행(대상 유실)은 대상 유실 404")
	void updatePhoneZeroRowsNotFound() {
		when(operatorMapper.selectOperatorByUserSn(TARGET)).thenReturn(operator(TARGET, "ACTIVE"));
		when(operatorMapper.updateOperatorPhone(anyLong(), anyString(), anyString(), anyLong())).thenReturn(0);

		var req = new OperatorUpdateRequest(String.valueOf(TARGET), null, null, "010-1234-5678");
		BusinessException e = assertThrows(BusinessException.class, () -> service.update(req, ACTOR));
		assertEquals(AccountErrorCode.OPERATOR_NOT_FOUND, e.getErrorCode());
	}
}
