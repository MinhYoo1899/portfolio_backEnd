package egovframework.eeum.auth.vo;

import java.util.List;

/**
 * 인증된 사용자 프린시펄. JWT access 토큰 클레임 ↔ SecurityContext principal 간 변환 단위.
 *
 * <p>SSO 분리 대비(D15/D16): 이 레코드가 "토큰이 보장하는 신원"의 전부다 —
 * 도메인 서비스는 USER_SN 만 신뢰하고 나머지는 표시용으로만 쓴다.
 *
 * @param userSn      사용자일련번호 (USER_ACNT PK — 행위주체 식별자)
 * @param emlAddr     이메일주소(로그인 ID)
 * @param userNm      사용자명
 * @param userTypeCd  사용자유형코드 (보조 — 권한 판단은 permissions 기준)
 * @param acntStts    계정상태 (PENDING/ACTIVE/...)
 * @param permissions 권한코드 목록 (데이터 주도 RBAC — ROLE 명 하드코딩 금지)
 */
public record AuthUser(
		Long userSn,
		String emlAddr,
		String userNm,
		String userTypeCd,
		String acntStts,
		List<String> permissions) {

	public AuthUser {
		permissions = permissions == null ? List.of() : List.copyOf(permissions);
	}
}
