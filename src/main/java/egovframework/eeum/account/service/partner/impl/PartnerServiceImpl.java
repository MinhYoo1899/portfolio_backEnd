package egovframework.eeum.account.service.partner.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.account.web.dto.PartnerDetailResponse;
import egovframework.eeum.account.web.dto.PartnerInsertRequest;
import egovframework.eeum.account.web.dto.PartnerListItemResponse;
import egovframework.eeum.account.web.dto.PartnerSearchRequest;
import egovframework.eeum.account.web.dto.PartnerSelfResponse;
import egovframework.eeum.account.web.dto.PartnerUpdateRequest;
import egovframework.eeum.account.vo.PartnerVO;
import egovframework.eeum.account.exception.AccountErrorCode;
import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.account.impl.AccountServiceImpl;
import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.service.partner.dao.PartnerMapper;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.audit.AuditRecorder;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.security.PasswordPolicy;
import egovframework.eeum.base.response.PageResponse;
import egovframework.eeum.terms.service.terms.TermsService;
import egovframework.eeum.notification.service.notification.NotificationEmitter;
import egovframework.eeum.notification.service.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파트너 서비스 구현 — 사업자 가입 신청 + 운영자 승인 워크플로우.
 *
 * <p>계정 생성/약관/역할 로직은 같은 도메인의 {@link AccountServiceImpl} 정적 헬퍼를 공유한다(도메인 내 공용).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerServiceImpl implements PartnerService {

	private static final String USER_TYPE_PARTNER = "PARTNER";
	private static final String LGN_TYPE_EMAIL = "EMAIL";
	/** 파트너 가입 직후 계정상태 — 운영자 승인 전 */
	private static final String SIGNUP_STTS = "PENDING";

	private static final String APRV_REQUESTED = "REQUESTED";
	private static final String APRV_APPROVED = "APPROVED";
	private static final String APRV_REJECTED = "REJECTED";

	private final PartnerMapper partnerMapper;
	private final AccountMapper accountMapper;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final AuditRecorder auditRecorder;
	private final TermsService termsService;
	private final NotificationEmitter notificationEmitter;

	@Value("${eeum.account.default-role.partner}")
	private String partnerDefaultRoleCd;

	@Override
	@Transactional
	@Audit(actn = "PARTNER_INSERT", trgtType = "PRTNR", trgtId = "#result.prtnrSn()")
	public PartnerDetailResponse insert(PartnerInsertRequest request, String cntnIp, String userAgnt) {
		if (accountMapper.existsActiveEmlAddr(request.emlAddr()) > 0) {
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE);
		}
		if (partnerMapper.existsActiveBrno(request.brno()) > 0) {
			throw new BusinessException(AccountErrorCode.PARTNER_BRNO_DUPLICATE);
		}
		passwordPolicy.validate(request.pswd()); // 가입 비밀번호 정책 단일화(codex MUST ④)
		AccountServiceImpl.validateEssentialTerms(termsService.getEffectiveTerms(), request.trmsAgreList());

		// 1) 로그인 계정 (PENDING — 승인 후 ACTIVE)
		AccountVO account = new AccountVO();
		account.setEmlAddr(request.emlAddr());
		account.setEnpswd(passwordEncoder.encode(request.pswd()));
		account.setUserTypeCd(USER_TYPE_PARTNER);
		account.setLgnType(LGN_TYPE_EMAIL);
		account.setAcntStts(SIGNUP_STTS);
		account.setUserNm(request.userNm());
		try {
			accountMapper.insertAccount(account);
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 동시 가입 경합 — UK_USER_ACNT_EML (codex 필수수정 2)
			throw new BusinessException(AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE,
					AccountErrorCode.ACCOUNT_EMAIL_DUPLICATE.defaultMessage(), e);
		}
		long userSn = account.getUserSn();

		AccountServiceImpl.recordTermsAgreements(accountMapper, userSn, request.trmsAgreList(),
				request.agreChnlOrDefault(), cntnIp, userAgnt);
		AccountServiceImpl.assignRole(accountMapper, userSn, partnerDefaultRoleCd);

		// 2) 파트너사업자 (REQUESTED)
		PartnerVO partner = new PartnerVO();
		partner.setUserSn(userSn);
		partner.setConm(request.conm());
		partner.setBrno(request.brno());
		partner.setRprsvNm(request.rprsvNm());
		partner.setAddr(request.addr());
		partner.setDaddr(request.daddr());
		partner.setMailOrderSalesNo(request.mailOrderSalesNo());
		partner.setPicNm(request.picNm());
		partner.setPicMblTelno(request.picMblTelno()); // TypeHandler 가 INSERT 시 암호화
		partner.setPrtnrEmlAddr(request.prtnrEmlAddr());
		try {
			partnerMapper.insertPartner(partner);
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 동시 신청 경합 — UK_PRTNR_BRNO/UK_PRTNR_USER (codex 필수수정 2)
			throw new BusinessException(AccountErrorCode.PARTNER_BRNO_DUPLICATE,
					AccountErrorCode.PARTNER_BRNO_DUPLICATE.defaultMessage(), e);
		}

		// 3) 승인이력 — 최초 신청 (행위자 = 신청자 본인)
		partnerMapper.insertApproveLog(partner.getPrtnrSn(), null, APRV_REQUESTED, "가입 신청", userSn);
		notificationEmitter.emit(userSn, "PARTNER_AUTH:" + partner.getPrtnrSn(), NotificationType.PARTNER_AUTH,
				"새 파트너 가입 신청", request.conm(), String.valueOf(partner.getPrtnrSn()));

		return detail(partner.getPrtnrSn());
	}

	@Override
	@Transactional(readOnly = true)
	public Long findManageablePrtnrSn(long userSn) {
		return partnerMapper.selectManageablePrtnrSnByUserSn(userSn);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<PartnerListItemResponse> list(PartnerSearchRequest request) {
		long total = partnerMapper.countPartnerList(request);
		List<PartnerListItemResponse> items = partnerMapper.selectPartnerList(request).stream()
				.map(PartnerListItemResponse::from)
				.toList();
		// 대량 열람 성격 — 검색조건 화이트리스트 JSON 으로 명시 기록 (codex 권고: 목록은 명시 호출)
		auditRecorder.recordSuccess(
				String.format("PARTNER_LIST_VIEW {\"page\":%d,\"size\":%d,\"aprvStts\":%s,\"resultCount\":%d}",
						request.getPage(), request.getPageSize(),
						request.getAprvStts() == null ? "null" : "\"" + request.getAprvStts() + "\"",
						items.size()),
				"PRTNR", null);
		return PageResponse.of(items, total, request.getPage(), request.getPageSize());
	}

	@Override
	@Transactional(readOnly = true)
	public long pendingCount() {
		// 인증요청(가입 승인 대기, aprvStts=REQUESTED) 수 — 헤더칩/사이드바 뱃지용. (brno 검증 대기는 별도, 검증 워크플로우와 재정의)
		return partnerMapper.countByAprvStts(APRV_REQUESTED);
	}

	@Override
	@Transactional(readOnly = true)
	@Audit(actn = "PARTNER_DETAIL_VIEW", trgtType = "PRTNR", trgtId = "#prtnrSn")
	public PartnerDetailResponse detail(long prtnrSn) {
		PartnerVO partner = partnerMapper.selectPartnerByPrtnrSn(prtnrSn);
		if (partner == null) {
			throw new BusinessException(AccountErrorCode.PARTNER_NOT_FOUND);
		}
		return PartnerDetailResponse.from(partner);
	}

	@Override
	@Transactional(readOnly = true)
	public PartnerSelfResponse me(long userSn) {
		// 본인 파트너 해석 — APPROVED+ACTIVE 만(비승인/정지/반려는 null). prtnrSn 은 토큰에서만, 파라미터 미수신(IDOR 차단).
		Long prtnrSn = partnerMapper.selectManageablePrtnrSnByUserSn(userSn);
		if (prtnrSn == null) {
			throw new BusinessException(AccountErrorCode.PARTNER_NOT_ACCESSIBLE); // 비승인/정지/해석실패 → 403 통일(P0)
		}
		PartnerVO partner = partnerMapper.selectPartnerByPrtnrSn(prtnrSn);
		if (partner == null) {
			throw new BusinessException(AccountErrorCode.PARTNER_NOT_ACCESSIBLE); // 동시 삭제 경합 방어 — 동일 403
		}
		return PartnerSelfResponse.from(partner);
	}

	@Override
	@Transactional
	@Audit(actn = "PARTNER_APPROVE", trgtType = "PRTNR", trgtId = "#prtnrSn")
	public void approve(long prtnrSn, long actorUserSn) {
		PartnerVO partner = requirePartner(prtnrSn);
		int updated = partnerMapper.updateAprvSttsIfRequested(prtnrSn, APRV_APPROVED, actorUserSn, null);
		if (updated == 0) {
			throw new BusinessException(AccountErrorCode.PARTNER_ALREADY_PROCESSED);
		}
		// 계정도 PENDING 일 때만 ACTIVE 로 — 탈퇴/정지 계정이면 0건 → 전체 롤백 (codex 필수수정 5)
		int acntUpdated = accountMapper.updateAcntSttsIfCurrent(partner.getUserSn(), "PENDING", "ACTIVE", actorUserSn);
		if (acntUpdated == 0) {
			throw BusinessException.conflict("계정 상태가 승인 가능한 상태가 아닙니다.");
		}
		partnerMapper.insertApproveLog(prtnrSn, APRV_REQUESTED, APRV_APPROVED, null, actorUserSn);
		log.info("[PARTNER] approved. prtnrSn={}, actor={}", prtnrSn, actorUserSn);
	}

	@Override
	@Transactional
	@Audit(actn = "PARTNER_REJECT", trgtType = "PRTNR", trgtId = "#prtnrSn")
	public void reject(long prtnrSn, String rjctRsn, long actorUserSn) {
		PartnerVO partner = requirePartner(prtnrSn);
		int updated = partnerMapper.updateAprvSttsIfRequested(prtnrSn, APRV_REJECTED, actorUserSn, rjctRsn);
		if (updated == 0) {
			throw new BusinessException(AccountErrorCode.PARTNER_ALREADY_PROCESSED);
		}
		int acntUpdated = accountMapper.updateAcntSttsIfCurrent(partner.getUserSn(), "PENDING", "REJECTED", actorUserSn);
		if (acntUpdated == 0) {
			throw BusinessException.conflict("계정 상태가 반려 가능한 상태가 아닙니다.");
		}
		partnerMapper.insertApproveLog(prtnrSn, APRV_REQUESTED, APRV_REJECTED, rjctRsn, actorUserSn);
		log.info("[PARTNER] rejected. prtnrSn={}, actor={}", prtnrSn, actorUserSn);
	}

	@Override
	@Transactional
	@Audit(actn = "PARTNER_INFO_UPDATE", trgtType = "PRTNR", trgtId = "#request.prtnrSn()")
	public PartnerDetailResponse updateInfo(PartnerUpdateRequest request, long actorUserSn) {
		long prtnrSn = request.prtnrSnAsLong();
		PartnerVO current = partnerMapper.selectPartnerByPrtnrSn(prtnrSn); // DEL_YN='N' + 계정 미탈퇴만 노출
		if (current == null) {
			throw new BusinessException(AccountErrorCode.PARTNER_NOT_FOUND);
		}
		// 수정 필드가 하나도 없으면(전부 null) 의미없는 audit-only UPDATE/감사로그 방지 — 400 (codex 권고)
		if (request.conm() == null && request.brno() == null && request.rprsvNm() == null
				&& request.addr() == null && request.daddr() == null && request.mailOrderSalesNo() == null
				&& request.picMblTelno() == null && request.picNm() == null && request.prtnrEmlAddr() == null) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "수정할 내용이 없습니다.");
		}
		// 필수 필드는 "전달 시 공백" 금지(클리어 차단). daddr/mailOrderSalesNo/picMblTelno 는 공백 클리어 허용. brno 형식/공백은 @Pattern 이 처리.
		requireNotBlankIfPresent(request.conm(), "상호");
		requireNotBlankIfPresent(request.rprsvNm(), "대표자명");
		requireNotBlankIfPresent(request.addr(), "사업장주소");
		requireNotBlankIfPresent(request.picNm(), "담당자명");
		requireNotBlankIfPresent(request.prtnrEmlAddr(), "파트너 대표 이메일");
		// brno 는 "실제 변경"일 때만 중복검사 + 검증상태 초기화(미변경이면 기존 brnoVrfcStts 유지)
		boolean brnoChanged = request.brno() != null && !request.brno().equals(current.getBrno());
		if (brnoChanged && partnerMapper.existsActiveBrno(request.brno()) > 0) {
			throw new BusinessException(AccountErrorCode.PARTNER_BRNO_DUPLICATE);
		}
		try {
			if (partnerMapper.updatePartnerInfo(prtnrSn, request.conm(), request.brno(), request.rprsvNm(),
					request.addr(), request.daddr(), request.mailOrderSalesNo(), request.picMblTelno(),
					request.picNm(), request.prtnrEmlAddr(), brnoChanged, actorUserSn) == 0) {
				throw new BusinessException(AccountErrorCode.PARTNER_NOT_FOUND);
			}
		} catch (org.springframework.dao.DuplicateKeyException e) {
			// 동시 경합 — UK_PRTNR_BRNO (서비스 선검사 + DB 유니크 2중 방어)
			throw new BusinessException(AccountErrorCode.PARTNER_BRNO_DUPLICATE,
					AccountErrorCode.PARTNER_BRNO_DUPLICATE.defaultMessage(), e);
		}
		return detail(prtnrSn);
	}

	private PartnerVO requirePartner(long prtnrSn) {
		PartnerVO partner = partnerMapper.selectPartnerByPrtnrSn(prtnrSn);
		if (partner == null) {
			throw new BusinessException(AccountErrorCode.PARTNER_NOT_FOUND);
		}
		return partner;
	}

	/** 필수 필드 — 전달됐는데(null 아님) 공백이면 검증오류(클리어 차단). null=미전달(변경안함)은 통과. */
	private static void requireNotBlankIfPresent(String value, String label) {
		if (value != null && value.isBlank()) {
			throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, label + "은(는) 공백일 수 없습니다.");
		}
	}
}
