package egovframework.eeum.account.service.account;

import java.util.List;

import egovframework.eeum.account.web.dto.AccountDeleteRequest;
import egovframework.eeum.account.web.dto.AccountDetailResponse;
import egovframework.eeum.account.web.dto.AccountInsertRequest;
import egovframework.eeum.account.web.dto.AccountUpdateRequest;
import egovframework.eeum.account.web.dto.PasswordChangeRequest;
import egovframework.eeum.account.web.dto.TrmsAgreItem;

/**
 * 계정 서비스 — 일반회원 가입 / 본인 조회·수정·탈퇴 / 소셜 가입(auth 도메인 소셜 로그인이 호출).
 */
public interface AccountService {

	/**
	 * 일반회원 가입 — 이메일 중복·필수약관 검증 후 계정/프로필/약관동의/기본역할 생성.
	 * 가입 직후 ACTIVE (전화 본인인증 연동 전 임시 정책).
	 */
	AccountDetailResponse insert(AccountInsertRequest request, String cntnIp, String userAgnt);

	/** 본인 계정 상세. */
	AccountDetailResponse detail(long userSn);

	/** 내 동의 현황 — 약관 유형별 현재 동의상태(유형당 최신 1건). */
	List<egovframework.eeum.account.web.dto.ConsentStatusResponse> getMyConsents(long userSn);

	/**
	 * 선택약관 동의 변경 — 시행 중 선택약관(ESNTL_YN='N')의 동의/철회를 새 이력 행으로 기록(append-only).
	 * 필수약관 또는 미시행 약관이면 거부.
	 */
	void changeConsent(long userSn, egovframework.eeum.account.web.dto.ConsentChangeRequest request,
			String cntnIp, String userAgnt);

	/** 본인 정보 수정(이름/별명). */
	void update(long userSn, AccountUpdateRequest request);

	/** 회원 탈퇴 — 비밀번호 본인확인 → soft delete + 탈퇴일시 + 전체 세션 회수. */
	void delete(long userSn, AccountDeleteRequest request);

	/**
	 * 비밀번호 변경(로그인 상태) — 현재 비밀번호 확인 후 새 비밀번호로 교체.
	 * 성공 시 전체 세션 무효화(refresh 폐기 + access 워터마크)로 재로그인이 필요하다.
	 * 소셜 전용 계정(비밀번호 미설정)은 변경 대상이 아니다.
	 */
	void changePassword(long userSn, PasswordChangeRequest request);

	/** 소셜 식별자(provider+providerUserId)로 연결된 사용자일련번호 조회 — 없으면 null(신규 가입 필요). */
	Long findUserSnBySocial(String prvdrCd, String prvdrUserId);

	/**
	 * 소셜 신규 가입 — 비밀번호 없는 계정(LGN_TYPE=SOCIAL) + 소셜연결 + 약관동의 + 기본역할 생성.
	 * 동일 이메일이 이미 있으면 자동연결하지 않고 거부(전략 §2 — 명시 연동은 2단계).
	 *
	 * @param emailVerified provider 의 email_verified — true 면 가입 시점에 EML_VRFD_YN='Y'/출처 GOOGLE(인증코드 미발송)
	 * @return 생성된 사용자일련번호
	 */
	long createSocialAccount(String prvdrCd, String prvdrUserId, String emlAddr, String userNm,
			boolean emailVerified, List<TrmsAgreItem> trmsAgreList, String agreChnlCd, String cntnIp, String userAgnt);
}
