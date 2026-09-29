package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.partner.dao.PartnerMapper;
import egovframework.eeum.account.service.partner.impl.PartnerServiceImpl;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.account.vo.PartnerVO;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.account.web.dto.PartnerInsertRequest;
import egovframework.eeum.account.web.dto.PartnerSelfResponse;
import egovframework.eeum.account.web.dto.PartnerUpdateRequest;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.security.PasswordPolicy;
import egovframework.eeum.terms.service.terms.TermsService;

/**
 * 파트너 사업자정보 수정(updateInfo) + mailOrderSalesNo wiring 검증 —
 * partial update/필수공백/중복/brno검증상태/상태불변/응답/통신판매업신고번호 round-trip(DB 없이 mock).
 */
class PartnerServiceVerification {

	private static final long PRTNR = 7L;
	private static final long ACTOR = 9L;
	private static final String MOSN = "2026-서울강남-01234";
	private static final String PIC_TEL = "010-1234-5678";

	private final PartnerMapper partnerMapper = mock(PartnerMapper.class);
	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
	private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
	private final TermsService termsService = mock(TermsService.class);
	private final PartnerServiceImpl service = new PartnerServiceImpl(
			partnerMapper, accountMapper, passwordEncoder, passwordPolicy, auditRecorder, termsService, mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));
	private static final jakarta.validation.Validator VALIDATOR =
			jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

	private static PartnerVO current(String brno) {
		PartnerVO p = new PartnerVO();
		p.setPrtnrSn(PRTNR);
		p.setConm("기존상호");
		p.setBrno(brno);
		p.setRprsvNm("기존대표");
		p.setAddr("기존주소");
		p.setDaddr("기존상세");
		p.setMailOrderSalesNo("기존신고번호");
		p.setBrnoVrfcStts("VERIFIED");
		p.setAprvStts("APPROVED");
		p.setAcntStts("ACTIVE");
		return p;
	}

	/** 5필드 부분수정 요청 — mailOrderSalesNo/picMblTelno 는 미전달(null). */
	private static PartnerUpdateRequest req(String conm, String brno, String rprsvNm, String addr, String daddr) {
		return new PartnerUpdateRequest(String.valueOf(PRTNR), conm, brno, rprsvNm, addr, daddr, null, null, null, null);
	}

	/** mailOrderSalesNo 만 전달하는 요청. */
	private static PartnerUpdateRequest reqMosn(String mailOrderSalesNo) {
		return new PartnerUpdateRequest(String.valueOf(PRTNR), null, null, null, null, null, mailOrderSalesNo, null, null, null);
	}

	/** picMblTelno 만 전달하는 요청. */
	private static PartnerUpdateRequest reqPic(String picMblTelno) {
		return new PartnerUpdateRequest(String.valueOf(PRTNR), null, null, null, null, null, null, picMblTelno, null, null);
	}

	/** picNm(담당자명) 만 전달하는 요청. */
	private static PartnerUpdateRequest reqPicNm(String picNm) {
		return new PartnerUpdateRequest(String.valueOf(PRTNR), null, null, null, null, null, null, null, picNm, null);
	}

	/** prtnrEmlAddr(회사 대표 이메일) 만 전달하는 요청. */
	private static PartnerUpdateRequest reqEmail(String prtnrEmlAddr) {
		return new PartnerUpdateRequest(String.valueOf(PRTNR), null, null, null, null, null, null, null, null, prtnrEmlAddr);
	}

	private void stubUpdateOk() {
		when(partnerMapper.updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong()))
				.thenReturn(1);
	}

	@Test
	@DisplayName("부분수정 — 전달 필드만 갱신, 나머지 null(보존), brno/mailOrderSalesNo 미전달이면 미변경")
	void partialUpdate() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(req("새상호", null, null, null, null), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, "새상호", null, null, null, null, null, null, null, null, false, ACTOR);
		verify(partnerMapper, never()).existsActiveBrno(anyString());
	}

	@Test
	@DisplayName("daddr 빈 문자열 — 클리어 허용(검증오류 아님)")
	void daddrClearAllowed() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(req(null, null, null, null, ""), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, "", null, null, null, null, false, ACTOR);
	}

	@Test
	@DisplayName("필수필드(conm) 공백 — 400, 매핑 미진입")
	void requiredBlankRejected() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(req("  ", null, null, null, null), ACTOR));
		assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	@Test
	@DisplayName("미존재/삭제 파트너 — 404, 매핑 미진입")
	void notFound() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(req("새상호", null, null, null, null), ACTOR));
		assertEquals(AccountErrorCode.PARTNER_NOT_FOUND, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	@Test
	@DisplayName("brno 변경 + 중복 — 409, 매핑 미진입(서비스 선검사)")
	void brnoDuplicateServiceCheck() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		when(partnerMapper.existsActiveBrno("2222222222")).thenReturn(1);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(req(null, "2222222222", null, null, null), ACTOR));
		assertEquals(AccountErrorCode.PARTNER_BRNO_DUPLICATE, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	@Test
	@DisplayName("brno 동시경합(DB UK) — 409로 번역")
	void brnoDuplicateDbRace() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		when(partnerMapper.existsActiveBrno("2222222222")).thenReturn(0);
		when(partnerMapper.updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong()))
				.thenThrow(new DuplicateKeyException("dup"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(req(null, "2222222222", null, null, null), ACTOR));
		assertEquals(AccountErrorCode.PARTNER_BRNO_DUPLICATE, e.getErrorCode());
	}

	@Test
	@DisplayName("brno 변경 — 사업자번호검증상태 REQUESTED 초기화(resetBrnoVrfc=true)")
	void brnoChangeResetsVrfc() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		when(partnerMapper.existsActiveBrno("2222222222")).thenReturn(0);
		stubUpdateOk();
		service.updateInfo(req(null, "2222222222", null, null, null), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, "2222222222", null, null, null, null, null, null, null, true, ACTOR);
	}

	@Test
	@DisplayName("brno 미변경(동일값) — 검증상태 유지(resetBrnoVrfc=false), 중복검사 미수행")
	void brnoUnchangedKeepsVrfc() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(req(null, "1111111111", null, null, null), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, "1111111111", null, null, null, null, null, null, null, false, ACTOR);
		verify(partnerMapper, never()).existsActiveBrno(anyString());
	}

	@Test
	@DisplayName("상태(승인/계정) 불변 — 승인/반려 갱신 경로 호출 안 함")
	void statusImmutable() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(req("새상호", null, null, null, null), ACTOR);
		verify(partnerMapper, never()).updateAprvSttsIfRequested(anyLong(), anyString(), anyLong(), any());
	}

	@Test
	@DisplayName("수정 후 최신 PartnerDetailResponse 반환(게이트 #2 사업자정보 4필드 포함)")
	void returnsUpdatedDetail() {
		PartnerVO before = current("1111111111");
		PartnerVO after = current("1111111111");
		after.setConm("새상호");
		after.setRprsvNm("새대표");
		after.setAddr("새주소");
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(before, after); // 본문 조회 → detail 재조회
		stubUpdateOk();
		var res = service.updateInfo(req("새상호", null, "새대표", "새주소", null), ACTOR);
		assertEquals("새상호", res.conm());
		assertEquals("새대표", res.rprsvNm());
		assertEquals("새주소", res.addr());
		assertNotNull(res.brno()); // 게이트 #2 = 상호/사업자번호/대표/주소 전부 응답에 존재
	}

	@Test
	@DisplayName("수정할 내용 없음(전부 null) — 400, 매핑 미진입(audit-only UPDATE 방지)")
	void allNullRejected() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(req(null, null, null, null, null), ACTOR));
		assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	// ---- mailOrderSalesNo(통신판매업신고번호) wiring ----

	@Test
	@DisplayName("mailOrderSalesNo 만 전달 — 400이 아니라 정상 수정(다른 필드 null 보존)")
	void mailOrderSalesNoOnlyUpdate() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqMosn(MOSN), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, MOSN, null, null, null, false, ACTOR);
	}

	@Test
	@DisplayName("mailOrderSalesNo 빈 문자열 — 클리어 허용(선택 필드, 검증오류 아님)")
	void mailOrderSalesNoClear() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqMosn(""), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, "", null, null, null, false, ACTOR);
	}

	@Test
	@DisplayName("mailOrderSalesNo 수정 후 detail 에 반영")
	void mailOrderSalesNoReflectedInDetail() {
		PartnerVO before = current("1111111111");
		PartnerVO after = current("1111111111");
		after.setMailOrderSalesNo(MOSN);
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(before, after);
		stubUpdateOk();
		var res = service.updateInfo(reqMosn(MOSN), ACTOR);
		assertEquals(MOSN, res.mailOrderSalesNo());
	}

	@Test
	@DisplayName("Bean Validation — mailOrderSalesNo 40자 초과는 제약위반(@Size), 40자는 통과")
	void mailOrderSalesNoLengthValidation() {
		assertFalse(VALIDATOR.validate(reqMosn("a".repeat(41))).isEmpty());
		assertTrue(VALIDATOR.validate(reqMosn("a".repeat(40))).isEmpty());
	}

	@Test
	@DisplayName("insert — mailOrderSalesNo 가 PartnerVO에 실려 영속되고 detail 에 노출(round-trip)")
	void insertCarriesMailOrderSalesNo() {
		when(accountMapper.existsActiveEmlAddr(anyString())).thenReturn(0);
		when(partnerMapper.existsActiveBrno(anyString())).thenReturn(0);
		when(termsService.getEffectiveTerms()).thenReturn(java.util.Collections.emptyList());
		// 기본 역할 배정(assignRole)은 roleSn==null 이면 ACCOUNT_ROLE_NOT_CONFIGURED — @Value 미주입(null) 대비 명시 스텁(codex 권고)
		org.springframework.test.util.ReflectionTestUtils.setField(service, "partnerDefaultRoleCd", "PARTNER");
		when(accountMapper.selectRoleSnByRoleCd("PARTNER")).thenReturn(1L);
		doAnswer(inv -> { inv.getArgument(0, AccountVO.class).setUserSn(100L); return 1; })
				.when(accountMapper).insertAccount(any());
		ArgumentCaptor<PartnerVO> captor = ArgumentCaptor.forClass(PartnerVO.class);
		doAnswer(inv -> { inv.getArgument(0, PartnerVO.class).setPrtnrSn(50L); return 1; })
				.when(partnerMapper).insertPartner(any());
		PartnerVO saved = current("1234567890");
		saved.setMailOrderSalesNo(MOSN);
		when(partnerMapper.selectPartnerByPrtnrSn(50L)).thenReturn(saved);

		PartnerInsertRequest req = new PartnerInsertRequest("p@eeum.kr", "Password1!", "김파트너",
				"고즈넉", "1234567890", "김대표", "서울", "1층", MOSN, "담당자", PIC_TEL, "co@eeum.kr",
				java.util.List.of(), "WEB");
		PartnerDetailResponse res = service.insert(req, "127.0.0.1", "ua");

		verify(partnerMapper).insertPartner(captor.capture());
		assertEquals(MOSN, captor.getValue().getMailOrderSalesNo());
		assertEquals(MOSN, res.mailOrderSalesNo());
	}

	// ---- picMblTelno(담당자 연락처) 암호화 wiring + 마스킹 ----

	@Test
	@DisplayName("detail — picMblTelno 는 마스킹값만 반환(전체값 노출 금지)")
	void detailMasksPicMblTelno() {
		PartnerVO vo = current("1111111111");
		vo.setPicMblTelno("01012345678"); // 복호화된 평문(매퍼 모킹) — 응답에서 마스킹되어야 함
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(vo);
		PartnerDetailResponse res = service.detail(PRTNR);
		assertEquals("010-****-5678", res.picMblTelno());
	}

	@Test
	@DisplayName("picMblTelno 만 전달 — 정상 수정(다른 필드 보존), 동적 SET 에 picMblTelno 반영(brno 미변경)")
	void picMblTelnoOnlyUpdate() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqPic("01055556666"), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, null, "01055556666", null, null, false, ACTOR);
		verify(partnerMapper, never()).existsActiveBrno(anyString());
	}

	@Test
	@DisplayName("picMblTelno 빈 문자열 — 클리어 허용(선택 필드, 검증오류 아님)")
	void picMblTelnoClear() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqPic(""), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, null, "", null, null, false, ACTOR);
	}

	@Test
	@DisplayName("insert — picMblTelno 가 PartnerVO 에 실리고(평문, 암호화는 TypeHandler 계층), detail 응답은 마스킹")
	void insertCarriesPicMblTelnoAndMasksDetail() {
		when(accountMapper.existsActiveEmlAddr(anyString())).thenReturn(0);
		when(partnerMapper.existsActiveBrno(anyString())).thenReturn(0);
		when(termsService.getEffectiveTerms()).thenReturn(java.util.Collections.emptyList());
		org.springframework.test.util.ReflectionTestUtils.setField(service, "partnerDefaultRoleCd", "PARTNER");
		when(accountMapper.selectRoleSnByRoleCd("PARTNER")).thenReturn(1L);
		doAnswer(inv -> { inv.getArgument(0, AccountVO.class).setUserSn(100L); return 1; })
				.when(accountMapper).insertAccount(any());
		ArgumentCaptor<PartnerVO> captor = ArgumentCaptor.forClass(PartnerVO.class);
		doAnswer(inv -> { inv.getArgument(0, PartnerVO.class).setPrtnrSn(50L); return 1; })
				.when(partnerMapper).insertPartner(any());
		PartnerVO saved = current("1234567890");
		saved.setPicMblTelno("01099998888"); // detail 재조회 시 복호화된 평문 — 응답 마스킹 대상
		when(partnerMapper.selectPartnerByPrtnrSn(50L)).thenReturn(saved);

		PartnerInsertRequest req = new PartnerInsertRequest("p@eeum.kr", "Password1!", "김파트너",
				"고즈넉", "1234567890", "김대표", "서울", "1층", MOSN, "담당자", "01012345678", "co@eeum.kr",
				java.util.List.of(), "WEB");
		PartnerDetailResponse res = service.insert(req, "127.0.0.1", "ua");

		verify(partnerMapper).insertPartner(captor.capture());
		assertEquals("01012345678", captor.getValue().getPicMblTelno()); // VO 에 평문 set(암호화는 mapper TypeHandler)
		assertEquals("010-****-8888", res.picMblTelno());                 // 응답은 마스킹값만
	}

	@Test
	@DisplayName("Bean Validation — picMblTelno 20자 초과는 제약위반(@Size), 20자는 통과")
	void picMblTelnoLengthValidation() {
		assertFalse(VALIDATOR.validate(reqPic("a".repeat(21))).isEmpty());
		assertTrue(VALIDATOR.validate(reqPic("a".repeat(20))).isEmpty());
	}

	// ---- 파트너 본인(PTNS) GET /partner/me ----

	@Test
	@DisplayName("me — APPROVED+ACTIVE 본인: PartnerSelfResponse 반환 + picMblTelno 마스킹")
	void meReturnsMaskedSelf() {
		when(partnerMapper.selectManageablePrtnrSnByUserSn(100L)).thenReturn(PRTNR);
		PartnerVO vo = current("1111111111");
		vo.setUserSn(100L);
		vo.setPicMblTelno("01012345678"); // 복호화된 평문 — 응답에서 마스킹되어야 함
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(vo);
		PartnerSelfResponse res = service.me(100L);
		assertEquals("010-****-5678", res.picMblTelno());
		assertEquals("기존상호", res.conm());
		assertEquals("100", res.userSn());
	}

	@Test
	@DisplayName("me — 비승인/정지/해석실패(null) → 403 PARTNER_NOT_ACCESSIBLE, 상세 미조회")
	void meForbiddenWhenNotManageable() {
		when(partnerMapper.selectManageablePrtnrSnByUserSn(100L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.me(100L));
		assertEquals(AccountErrorCode.PARTNER_NOT_ACCESSIBLE, e.getErrorCode());
		verify(partnerMapper, never()).selectPartnerByPrtnrSn(anyLong());
	}

	@Test
	@DisplayName("Bean Validation — brno 형식오류는 제약위반(@Pattern), 정상 10자리는 통과")
	void brnoPatternValidation() {
		assertFalse(VALIDATOR.validate(req(null, "12345", null, null, null)).isEmpty()); // 10자리 아님
		assertTrue(VALIDATOR.validate(req(null, "1234567890", null, null, null)).isEmpty()); // 정상
	}

	@Test
	@DisplayName("Bean Validation — prtnrSn 공백은 제약위반(@NotBlank)")
	void prtnrSnNotBlankValidation() {
		assertFalse(VALIDATOR.validate(new PartnerUpdateRequest("", null, null, null, null, null, null, null, null, null)).isEmpty());
	}

	// ---- [2️⃣ 순수 add] picNm / prtnrEmlAddr wiring + pending-count + 목록 매핑·연락처 미노출 정책 ----

	@Test
	@DisplayName("picNm 단독 수정 — 정상 배선(다른 필드 null 보존), 신규 필드 수정은 resetBrnoVrfc=false")
	void picNmOnlyUpdate() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqPicNm("김담당"), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, null, null, "김담당", null, false, ACTOR);
		verify(partnerMapper, never()).existsActiveBrno(anyString());
	}

	@Test
	@DisplayName("prtnrEmlAddr(회사이메일) 단독 수정 — 정상 배선, 이메일 수정만으로 brno 검증상태 초기화 안 함(resetBrnoVrfc=false)")
	void prtnrEmlAddrOnlyUpdate() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqEmail("biz@hanok.kr"), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, null, null, null, "biz@hanok.kr", false, ACTOR);
		verify(partnerMapper, never()).existsActiveBrno(anyString());
	}

	@Test
	@DisplayName("picNm/prtnrEmlAddr 미전달(null) — 다른 필드 수정 시 두 필드는 null 로 전달(기존값 보존)")
	void picFieldsNullPreserved() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		stubUpdateOk();
		service.updateInfo(reqPic("01099998888"), ACTOR);
		verify(partnerMapper).updatePartnerInfo(PRTNR, null, null, null, null, null, null, "01099998888", null, null, false, ACTOR);
	}

	@Test
	@DisplayName("picNm 공백 — 400(클리어 차단), 매핑 미진입")
	void picNmBlankRejected() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(reqPicNm("  "), ACTOR));
		assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	@Test
	@DisplayName("prtnrEmlAddr 공백 — 400(클리어 차단), 매핑 미진입")
	void prtnrEmlAddrBlankRejected() {
		when(partnerMapper.selectPartnerByPrtnrSn(PRTNR)).thenReturn(current("1111111111"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.updateInfo(reqEmail(""), ACTOR));
		assertEquals(CommonErrorCode.VALIDATION_ERROR, e.getErrorCode());
		verify(partnerMapper, never()).updatePartnerInfo(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
	}

	@Test
	@DisplayName("Bean Validation — prtnrEmlAddr 이메일 형식 오류는 제약위반(@Email), 정상 이메일은 통과")
	void prtnrEmlAddrEmailValidation() {
		assertFalse(VALIDATOR.validate(reqEmail("not-an-email")).isEmpty());
		assertTrue(VALIDATOR.validate(reqEmail("biz@hanok.kr")).isEmpty());
	}

	@Test
	@DisplayName("pendingCount — REQUESTED(승인대기) 수를 조회해 반환")
	void pendingCountReturnsRequested() {
		when(partnerMapper.countByAprvStts("REQUESTED")).thenReturn(3L);
		assertEquals(3L, service.pendingCount());
		verify(partnerMapper).countByAprvStts("REQUESTED");
	}

	@Test
	@DisplayName("PartnerPendingCountResponse — requestedCount 계약")
	void pendingCountResponseContract() {
		var res = egovframework.eeum.account.web.dto.PartnerPendingCountResponse.of(5L);
		assertEquals(5L, res.requestedCount());
	}

	@Test
	@DisplayName("PartnerListItemResponse.from — rprsvNm/picNm/prtnrEmlAddr/houseCnt 매핑")
	void listItemMapsNewFields() {
		PartnerVO vo = current("1111111111");
		vo.setUserNm("가입자");
		vo.setEmlAddr("login@eeum.kr");
		vo.setPicNm("김담당");
		vo.setPrtnrEmlAddr("biz@hanok.kr");
		vo.setHouseCnt(4);
		var item = egovframework.eeum.account.web.dto.PartnerListItemResponse.from(vo);
		assertEquals("기존대표", item.rprsvNm());
		assertEquals("김담당", item.picNm());
		assertEquals("biz@hanok.kr", item.prtnrEmlAddr());
		assertEquals(Integer.valueOf(4), item.houseCnt());
	}

	@Test
	@DisplayName("목록 응답 계약 — picMblTelno(담당자 연락처)는 목록에 존재하지 않음(대량열람 복호화·노출 차단, codex MUST)")
	void listItemHasNoPicMblTelno() {
		boolean hasPicMblTelno = java.util.Arrays.stream(
				egovframework.eeum.account.web.dto.PartnerListItemResponse.class.getRecordComponents())
				.anyMatch(c -> c.getName().equals("picMblTelno"));
		assertFalse(hasPicMblTelno, "목록 응답에 picMblTelno 가 있으면 안 됨");
	}
}
