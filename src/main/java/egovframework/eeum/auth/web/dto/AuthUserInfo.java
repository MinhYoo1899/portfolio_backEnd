package egovframework.eeum.auth.web.dto;

import java.util.List;

import egovframework.eeum.auth.vo.AuthUser;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증 사용자 정보 DTO (로그인 응답·/auth/me 공용).
 *
 * <p>프론트 가이드 §4 권한 임시 스키마와 호환: {@code userSn}(문자열)·{@code userTypeCd}·{@code permissions}.
 *
 * @param userSn      사용자일련번호 (ID 는 문자열 규약 — frontend-api-guide §6)
 * @param emlAddr     이메일주소
 * @param userNm      사용자명
 * @param userTypeCd  사용자유형코드
 * @param acntStts    계정상태 (PENDING=파트너 승인대기 등 — 프론트는 이 값으로 승인대기 화면 분기)
 * @param permissions 권한코드 목록 (데이터 주도 RBAC)
 */
@Schema(description = "인증 사용자 정보")
public record AuthUserInfo(
		@Schema(description = "사용자일련번호(문자열)", example = "1024") String userSn,
		@Schema(description = "이메일주소") String emlAddr,
		@Schema(description = "사용자명") String userNm,
		@Schema(description = "사용자유형코드", example = "MEMBER") String userTypeCd,
		@Schema(description = "계정상태(PENDING=승인대기)", example = "ACTIVE") String acntStts,
		@Schema(description = "권한코드 목록") List<String> permissions) {

	public static AuthUserInfo from(AuthUser user) {
		return new AuthUserInfo(
				String.valueOf(user.userSn()),
				user.emlAddr(),
				user.userNm(),
				user.userTypeCd(),
				user.acntStts(),
				user.permissions());
	}
}
